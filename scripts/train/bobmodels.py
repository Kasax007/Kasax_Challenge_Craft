"""What the trainer learns from Bob's journal.

The cover model: when a fight goes badly, which way out (fight on, wall himself in, pillar up, run,
dig in) - for the situation (Bob's hearts and gear, the place, what is after him; 42 numbers, see
Situation.java) a small neural network gives, for each way, the chance of dying, the hearts lost
and the seconds it takes. Trained on the arena's fights, where the way was picked at random among
those possible (so every way is seen everywhere) - and on the games, at a lower weight.

The goal model: how long a Lockout tile really takes, from the plan's own estimate and the
situation (25 numbers, see GoalModel.java) - a ridge regression on the logarithm of the seconds,
and a logistic one for the chance it does not come off.

Both are exported as JSON the mod reads (bob-models/cover.json, goal.json). numpy is enough; with
PyTorch installed (and a CUDA GPU) the cover model trains there.
"""
import glob
import json
import math
import os
import random
import time

import numpy as np

OPTIONS = ["FIGHT", "BOX", "PILLAR", "PILLAR_FIGHT", "RUN", "DIG", "SHELTER"]
COVER_FEATURES = 42
GOAL_FEATURES = 25
HEADS = ["death", "hp", "secs"]
# What a death costs against a heart and a second (the same weights go into the model file).
COST = {"death": 60.0, "hp": 1.0, "secs": 0.05}


# ---- reading the journal ------------------------------------------------------------------------

def iter_records(journal_dir, kinds=None):
    """Every complete record in the journal files (a line still being written is skipped)."""
    for path in sorted(glob.glob(os.path.join(journal_dir, "**", "*.jsonl"), recursive=True)):
        try:
            with open(path, encoding="utf-8", errors="replace") as f:
                for line in f:
                    if not line.endswith("\n"):
                        break
                    try:
                        r = json.loads(line)
                    except ValueError:
                        continue
                    if kinds is None or r.get("t") in kinds:
                        yield r
        except OSError:
            continue


def journal_size(journal_dir):
    return sum(os.path.getsize(p) for p in glob.glob(os.path.join(journal_dir, "**", "*.jsonl"), recursive=True))


def prune_journal(journal_dir, max_bytes):
    """Oldest files away once the journal is bigger than {max_bytes}."""
    files = sorted(glob.glob(os.path.join(journal_dir, "**", "*.jsonl"), recursive=True), key=os.path.getmtime)
    total = sum(os.path.getsize(p) for p in files)
    removed = 0
    while files and total > max_bytes:
        p = files.pop(0)
        total -= os.path.getsize(p)
        os.remove(p)
        removed += 1
    return removed


def cost_of(died, hp_lost, secs):
    return COST["death"] * died + COST["hp"] * hp_lost + COST["secs"] * secs


# ---- the cover model ----------------------------------------------------------------------------

class CoverData:
    def __init__(self, records):
        X, opt, mask, died, hp, secs, w, explore, rule = [], [], [], [], [], [], [], [], []
        for r in records:
            x = r.get("x")
            if not x or len(x) != COVER_FEATURES or r.get("option") not in OPTIONS:
                continue
            m = [0.0] * len(OPTIONS)
            for o in r.get("possible", []):
                if o in OPTIONS:
                    m[OPTIONS.index(o)] = 1.0
            o = OPTIONS.index(r["option"])
            m[o] = 1.0
            how = r.get("how", "rule")
            X.append(x)
            opt.append(o)
            mask.append(m)
            died.append(float(r.get("died", 0)))
            hp.append(min(20.0, float(r.get("hp_lost", 0))))
            secs.append(min(60.0, float(r.get("secs", 0))))
            # (The arena's random picks tell each way's worth as it is; the rules' and the
            # model's own picks lean towards where they pick it - counted at half.)
            w.append(1.0 if how == "explore" else 0.5)
            explore.append(how == "explore")
            rule.append(OPTIONS.index(r["rule"]) if r.get("rule") in OPTIONS else o)
        self.X = np.asarray(X, dtype=np.float64).reshape(-1, COVER_FEATURES)
        self.opt = np.asarray(opt, dtype=np.int64)
        self.mask = np.asarray(mask, dtype=np.float64).reshape(-1, len(OPTIONS))
        self.died = np.asarray(died)
        self.hp = np.asarray(hp)
        self.secs = np.asarray(secs)
        self.w = np.asarray(w)
        self.explore = np.asarray(explore, dtype=bool)
        self.rule = np.asarray(rule, dtype=np.int64)

    def __len__(self):
        return len(self.opt)

    def subset(self, idx):
        d = CoverData([])
        for k in ("X", "opt", "mask", "died", "hp", "secs", "w", "explore", "rule"):
            setattr(d, k, getattr(self, k)[idx])
        return d

    def costs(self):
        return cost_of(self.died, self.hp, self.secs)


def _huber(err, delta):
    a = np.abs(err)
    return np.where(a <= delta, 0.5 * err * err, delta * (a - 0.5 * delta)), np.clip(err, -delta, delta)


class NumpyNet:
    """A plain MLP (ReLU) with Adam, enough for a few hundred thousand fights on a CPU."""

    def __init__(self, sizes, seed=0):
        rng = np.random.default_rng(seed)
        self.W = [rng.normal(0, math.sqrt(2.0 / a), size=(a, b)) for a, b in zip(sizes[:-1], sizes[1:])]
        self.b = [np.zeros(b) for b in sizes[1:]]
        self.m = [np.zeros_like(p) for p in self.W + self.b]
        self.v = [np.zeros_like(p) for p in self.W + self.b]
        self.t = 0

    def forward(self, X):
        hs = [X]
        h = X
        for i, (W, b) in enumerate(zip(self.W, self.b)):
            z = h @ W + b
            h = np.maximum(z, 0) if i < len(self.W) - 1 else z
            hs.append(h)
        return hs

    def backward(self, hs, grad_out):
        gW, gb = [None] * len(self.W), [None] * len(self.b)
        g = grad_out
        for i in reversed(range(len(self.W))):
            gW[i] = hs[i].T @ g
            gb[i] = g.sum(axis=0)
            if i > 0:
                g = (g @ self.W[i].T) * (hs[i] > 0)
        return gW, gb

    def step(self, gW, gb, lr=1e-3, wd=1e-4):
        self.t += 1
        params = self.W + self.b
        grads = [gw + wd * w for gw, w in zip(gW, self.W)] + gb
        for i, (p, g) in enumerate(zip(params, grads)):
            self.m[i] = 0.9 * self.m[i] + 0.1 * g
            self.v[i] = 0.999 * self.v[i] + 0.001 * g * g
            mh = self.m[i] / (1 - 0.9 ** self.t)
            vh = self.v[i] / (1 - 0.999 ** self.t)
            p -= lr * mh / (np.sqrt(vh) + 1e-8)

    def predict(self, X):
        return self.forward(X)[-1]

    def layers(self):
        out = []
        for i, (W, b) in enumerate(zip(self.W, self.b)):
            out.append({"w": W.T.round(6).tolist(), "b": b.round(6).tolist(), "act": "relu" if i < len(self.W) - 1 else "linear"})
        return out


def _cover_loss_grad(out, d, idx):
    """Loss on the way taken only (the others were not tried in that fight); gradient on the outputs."""
    n = len(idx)
    k = len(HEADS)
    o = d.opt[idx]
    rows = np.arange(n)
    w = d.w[idx] / max(1e-9, d.w[idx].sum())
    zd, zh, zs = out[rows, o * k], out[rows, o * k + 1], out[rows, o * k + 2]
    p = 1 / (1 + np.exp(-np.clip(zd, -40, 40)))
    y = d.died[idx]
    bce = -(y * np.log(p + 1e-9) + (1 - y) * np.log(1 - p + 1e-9))
    lh, gh = _huber(zh - d.hp[idx], 4.0)
    ls, gs = _huber(zs - d.secs[idx], 10.0)
    # (Weighted as the costs weigh them: a death is what counts, hearts next, seconds a little.)
    loss = (w * (3.0 * bce + lh / 4.0 + ls / 40.0)).sum()
    g = np.zeros_like(out)
    g[rows, o * k] = w * 3.0 * (p - y)
    g[rows, o * k + 1] = w * gh / 4.0
    g[rows, o * k + 2] = w * gs / 40.0
    return loss, g


def train_cover(data, hidden=64, epochs=300, batch=256, lr=2e-3, seed=0, patience=25, use_torch=None):
    """Trains the cover model; returns (model dict for export, metrics)."""
    n = len(data)
    rng = np.random.default_rng(seed)
    idx = rng.permutation(n)
    val_n = max(1, n // 5)
    val, tr = idx[:val_n], idx[val_n:]
    mean = data.X[tr].mean(axis=0)
    std = data.X[tr].std(axis=0)
    std[std < 1e-6] = 1.0
    Xs = (data.X - mean) / std
    sizes = [COVER_FEATURES, hidden, hidden, len(OPTIONS) * len(HEADS)]
    torch_mod = None
    if use_torch is not False:
        try:
            import torch
            torch_mod = torch
        except ImportError:
            torch_mod = None
    t0 = time.time()
    if torch_mod is not None:
        layers, best_val, device, epochs_run = _train_cover_torch(torch_mod, Xs, data, tr, val, sizes, epochs, batch, lr, seed, patience)
    else:
        layers, best_val, epochs_run = _train_cover_numpy(Xs, data, tr, val, sizes, epochs, batch, lr, seed, patience)
        device = "cpu (numpy)"
    model = {"kind": "cover", "version": time.strftime("%Y%m%d-%H%M%S"), "inputs": COVER_FEATURES, "mean": mean.round(6).tolist(),
             "std": std.round(6).tolist(), "layers": layers, "options": OPTIONS, "heads": HEADS, "cost": COST, "trained_on": int(n)}
    metrics = {"n": int(n), "n_explore": int(data.explore.sum()), "val_loss": round(float(best_val), 4), "epochs": epochs_run,
               "seconds": round(time.time() - t0, 1), "device": device}
    return model, metrics


def _train_cover_numpy(Xs, data, tr, val, sizes, epochs, batch, lr, seed, patience):
    net = NumpyNet(sizes, seed)
    rng = np.random.default_rng(seed + 1)
    best, best_layers, bad, e = float("inf"), net.layers(), 0, 0
    for e in range(epochs):
        order = rng.permutation(tr)
        for s in range(0, len(order), batch):
            b = order[s:s + batch]
            hs = net.forward(Xs[b])
            _, g = _cover_loss_grad(hs[-1], data, b)
            gW, gb = net.backward(hs, g)
            net.step(gW, gb, lr)
        vl, _ = _cover_loss_grad(net.predict(Xs[val]), data, val)
        if vl < best - 1e-5:
            best, best_layers, bad = vl, net.layers(), 0
        else:
            bad += 1
            if bad >= patience:
                break
    return best_layers, best, e + 1


def _train_cover_torch(torch, Xs, data, tr, val, sizes, epochs, batch, lr, seed, patience):
    device = "cuda" if torch.cuda.is_available() else "cpu"
    torch.manual_seed(seed)
    mods = []
    for i, (a, b) in enumerate(zip(sizes[:-1], sizes[1:])):
        mods.append(torch.nn.Linear(a, b))
        if i < len(sizes) - 2:
            mods.append(torch.nn.ReLU())
    net = torch.nn.Sequential(*mods).to(device).double()
    opt = torch.optim.Adam(net.parameters(), lr=lr, weight_decay=1e-4)
    X = torch.tensor(Xs, device=device)
    k = len(HEADS)
    o = torch.tensor(data.opt, device=device)
    died = torch.tensor(data.died, device=device)
    hp = torch.tensor(data.hp, device=device)
    secs = torch.tensor(data.secs, device=device)
    w = torch.tensor(data.w, device=device)

    def loss_on(ix):
        out = net(X[ix])
        oo = o[ix]
        r = torch.arange(len(ix), device=device)
        zd, zh, zs = out[r, oo * k], out[r, oo * k + 1], out[r, oo * k + 2]
        ww = w[ix] / w[ix].sum()
        bce = torch.nn.functional.binary_cross_entropy_with_logits(zd, died[ix], reduction="none")
        lh = torch.nn.functional.huber_loss(zh, hp[ix], reduction="none", delta=4.0)
        ls = torch.nn.functional.huber_loss(zs, secs[ix], reduction="none", delta=10.0)
        return (ww * (3.0 * bce + lh / 4.0 + ls / 40.0)).sum()

    tr_t = torch.tensor(tr, device=device)
    val_t = torch.tensor(val, device=device)
    best, best_state, bad, e = float("inf"), None, 0, 0
    for e in range(epochs):
        perm = tr_t[torch.randperm(len(tr_t), device=device)]
        for s in range(0, len(perm), batch):
            opt.zero_grad()
            loss = loss_on(perm[s:s + batch])
            loss.backward()
            opt.step()
        with torch.no_grad():
            vl = float(loss_on(val_t))
        if vl < best - 1e-5:
            best, bad = vl, 0
            best_state = {kk: v.detach().cpu().clone() for kk, v in net.state_dict().items()}
        else:
            bad += 1
            if bad >= patience:
                break
    net.load_state_dict(best_state)
    layers = []
    lin = [m for m in net if isinstance(m, torch.nn.Linear)]
    for i, m in enumerate(lin):
        layers.append({"w": m.weight.detach().cpu().numpy().round(6).tolist(), "b": m.bias.detach().cpu().numpy().round(6).tolist(),
                       "act": "relu" if i < len(lin) - 1 else "linear"})
    return layers, best, device, e + 1


def cover_predict(model, X):
    """The model's outputs as the mod computes them: per option (death chance, hearts, seconds)."""
    mean, std = np.asarray(model["mean"]), np.asarray(model["std"])
    h = (np.asarray(X, dtype=np.float64) - mean) / np.where(std > 1e-9, std, 1)
    for layer in model["layers"]:
        W = np.asarray(layer["w"]).T
        h = h @ W + np.asarray(layer["b"])
        if layer["act"] == "relu":
            h = np.maximum(h, 0)
    k = len(HEADS)
    out = h.reshape(len(h), len(model["options"]), k)
    death = 1 / (1 + np.exp(-np.clip(out[:, :, 0], -40, 40)))
    return death, np.maximum(0, out[:, :, 1]), np.maximum(0, out[:, :, 2])


def cover_choices(model, data):
    death, hp, secs = cover_predict(model, data.X)
    c = model["cost"]
    cost = c["death"] * death + c["hp"] * hp + c["secs"] * secs
    cost = np.where(data.mask > 0, cost, np.inf)
    return np.argmin(cost, axis=1)


def offline_value(data, choices):
    """What a policy would cost, judged on the arena's random picks (inverse propensity): each fight
    where the policy's choice is the one that was picked counts as many times as there were ways."""
    ex = np.where(data.explore)[0]
    if len(ex) == 0:
        return None, 0
    ways = data.mask[ex].sum(axis=1)
    hit = choices[ex] == data.opt[ex]
    contrib = np.where(hit, data.costs()[ex] * ways, 0.0)
    # (Self-normalised: divided by the weight that matched, not by the count - steadier.)
    weight = np.where(hit, ways, 0.0)
    if weight.sum() <= 0:
        return None, 0
    return float(contrib.sum() / weight.sum()), int(hit.sum())


def bootstrap_diff(data, a, b, rounds=300, seed=0):
    """How sure a is cheaper than b (share of bootstrap rounds where it is), on the random picks."""
    ex = np.where(data.explore)[0]
    if len(ex) < 30:
        return 0.0
    rng = np.random.default_rng(seed)
    wins = 0
    for _ in range(rounds):
        s = ex[rng.integers(0, len(ex), len(ex))]
        d = data.subset(s)
        va, _ = offline_value(d, a[s])
        vb, _ = offline_value(d, b[s])
        if va is not None and vb is not None and va < vb:
            wins += 1
    return wins / rounds


def evaluate_cover(model, data):
    choices = cover_choices(model, data)
    v_model, n_model = offline_value(data, choices)
    v_rule, n_rule = offline_value(data, data.rule)
    better = bootstrap_diff(data, choices, data.rule)
    counts = {OPTIONS[i]: int((choices == i).sum()) for i in range(len(OPTIONS))}
    return {"value_model": v_model, "value_rule": v_rule, "matched_model": n_model, "matched_rule": n_rule,
            "p_better": round(better, 3), "choices": counts}


# ---- the goal model -----------------------------------------------------------------------------

FAILED = {"failed", "overrun", "died"}


def goal_data(records):
    X, y, fail, plain, done = [], [], [], [], []
    for r in records:
        x = r.get("x")
        if not x or len(x) != GOAL_FEATURES or r.get("proxy"):
            continue
        out = r.get("outcome")
        if out not in FAILED and out != "done":
            continue
        X.append(x)
        secs = max(1.0, float(r.get("secs", 1)))
        y.append(math.log1p(secs))
        fail.append(1.0 if out in FAILED else 0.0)
        plain.append(float(r.get("plain", math.expm1(x[0]))))
        done.append(out == "done")
    return (np.asarray(X, dtype=np.float64).reshape(-1, GOAL_FEATURES), np.asarray(y), np.asarray(fail), np.asarray(plain),
            np.asarray(done, dtype=bool))


def _ridge(Xs, y, lam):
    n, d = Xs.shape
    A = np.hstack([Xs, np.ones((n, 1))])
    reg = lam * np.eye(d + 1)
    reg[-1, -1] = 0
    coef = np.linalg.solve(A.T @ A + reg, A.T @ y)
    return coef[:-1], coef[-1]


def _logistic(Xs, y, lam=1.0, iters=50):
    n, d = Xs.shape
    w, b = np.zeros(d), 0.0
    for _ in range(iters):
        z = Xs @ w + b
        p = 1 / (1 + np.exp(-np.clip(z, -40, 40)))
        g = Xs.T @ (p - y) + lam * w
        gb = (p - y).sum()
        s = p * (1 - p) + 1e-6
        H = (Xs.T * s) @ Xs + lam * np.eye(d)
        hb = s.sum()
        w -= np.linalg.solve(H, g)
        b -= gb / hb
    return w, b


def train_goal(records, seed=0):
    X, y, fail, plain, done = goal_data(records)
    n_done = int(done.sum())
    if n_done < 20:
        return None, {"n": int(len(y)), "n_done": n_done, "note": "zu wenig Daten"}
    rng = np.random.default_rng(seed)
    idx = rng.permutation(len(y))
    val_n = max(1, len(y) // 5)
    val, tr = idx[:val_n], idx[val_n:]
    mean = X[tr].mean(axis=0)
    std = X[tr].std(axis=0)
    std[std < 1e-6] = 1.0
    Xs = (X - mean) / std
    trd, vald = tr[done[tr]], val[done[val]]
    best = None
    for lam in (0.1, 1, 10, 100, 1000):
        w, b = _ridge(Xs[trd], y[trd], lam)
        mae = float(np.abs(Xs[vald] @ w + b - y[vald]).mean()) if len(vald) else float("inf")
        if best is None or mae < best[0]:
            best = (mae, lam, w, b)
    mae, lam, w, b = best
    w, b = _ridge(Xs[done], y[done], lam)
    rw, rb = _logistic(Xs, fail, lam=1.0)
    base = float(np.abs(np.log1p(plain[vald]) - y[vald]).mean()) if len(vald) else float("inf")
    model = {"kind": "goal", "version": time.strftime("%Y%m%d-%H%M%S"), "mean": mean.round(6).tolist(), "std": std.round(6).tolist(),
             "w": w.round(6).tolist(), "b": round(float(b), 6), "risk_w": rw.round(6).tolist(), "risk_b": round(float(rb), 6),
             "min_ratio": 0.5, "max_ratio": 3.0, "trained_on": int(len(y))}
    metrics = {"n": int(len(y)), "n_done": n_done, "fail_rate": round(float(fail.mean()), 3), "lambda": lam,
               "mae_log_model": round(mae, 4), "mae_log_plan": round(base, 4),
               "gain": round(1 - mae / base, 3) if base > 0 and base != float("inf") else None}
    return model, metrics
