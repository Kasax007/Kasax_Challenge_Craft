package net.kasax.challengecraft.bot.lockout.sim;

/**
 * One goal of the pool as the abstract game sees it: the milestones it needs, the resources it uses
 * up, where its own work is done and how long that takes (a good player's seconds), and what in a
 * player's progress claims it by itself (a furnace made claims "Furnace" whatever it was made for).
 *
 * <p>Own work: {@code work} seconds, of which the share {@code luck} is searching (exponential: it
 * may come at once or late). A monster's goal is a search and a fight: the search follows the
 * monsters (the dark), the fight carries {@code hazard} extra deaths per minute.
 */
public final class Recipe {
    public final String id;
    /** Index in {@link GoalBook}. */
    public int index;
    public int needs;
    public int[] uses = new int[0];
    /** Where the own work is done; null: anywhere (crafting) or, for gathering, on the surface where it stands. */
    public Place place;
    public double work, luck;
    public Econ.Work kind = Econ.Work.GATHER;
    /** A monster hunt: the search ({@code work * luck}) is quicker in the dark. */
    public boolean monster;
    public double hazard;
    /** Only fought geared (a shield or armour): an enderman, a witch, a blaze... */
    public boolean heavy;
    /** Only at night (a bed to sleep in). */
    public boolean nightOnly;
    /** Not before this game second (phantoms come after three sleepless nights). */
    public double notBefore;
    /** Claimed by itself once this milestone is in hand (-1: none). */
    public int auto = -1;
    /** Claimed by itself once this many of this resource are in hand (-1: none). */
    public int autoRes = -1, autoN = 1;
    /** Whether Bob knows a way to it (LockoutGoals.covers); the others he leaves alone. */
    public boolean bob = true;
    /** No way at all within a Lockout game (the End, silk touch only...). */
    public boolean impossible;
    /**
     * What the leagues' journals saw of it (see {@link GoalBook}): attempts counted and how many
     * got the tile - the simulator's failure chance for this goal (Sim.failChance).
     */
    public int seenStarts, seenDone;
    /**
     * Claimed by the way: per minute on the surface at night or down in the caves, the chance the
     * player meets and gets it without going for it (a zombie at the door, an arrow on the shield) -
     * armed with stone tools at least ({@link Ms#STONE}); 0: never.
     */
    public double chance;
    /** For {@link #chance}: only with this milestone too (-1: none) - the arrow blocked wants the shield. */
    public int chanceNeeds = -1;

    Recipe(String id) {
        this.id = id;
    }

    // ---- builder -----------------------------------------------------------------------------

    Recipe ms(Ms... ms) {
        for (Ms m : ms) needs |= m.bit;
        return this;
    }

    Recipe use(Res r, int n) {
        int[] u = java.util.Arrays.copyOf(uses, uses.length + 2);
        u[uses.length] = r.ordinal();
        u[uses.length + 1] = n;
        uses = u;
        return this;
    }

    Recipe at(Place p) {
        place = p;
        return this;
    }

    Recipe work(double seconds, double luckShare, Econ.Work k) {
        work = seconds;
        luck = luckShare;
        kind = k;
        return this;
    }

    Recipe craft(double seconds) {
        return work(seconds, 0, Econ.Work.CRAFT);
    }

    Recipe gather(double seconds, double luckShare) {
        return work(seconds, luckShare, Econ.Work.GATHER);
    }

    Recipe explore(double seconds) {
        return work(seconds, 0.2, Econ.Work.EXPLORE);
    }

    /** A monster: {@code search} seconds to find one where it lives (in the dark), {@code fight} to kill it. */
    Recipe monster(double search, double fight, double hazardPerMinute) {
        work = search + fight;
        luck = search / Math.max(1, search + fight);
        kind = Econ.Work.MONSTER;
        monster = true;
        hazard = hazardPerMinute;
        return this;
    }

    Recipe animal(double seconds, double luckShare) {
        return work(seconds, luckShare, Econ.Work.ANIMAL);
    }

    Recipe nether(double seconds, double luckShare) {
        return work(seconds, luckShare, Econ.Work.NETHER);
    }

    Recipe trade(double seconds, double luckShare) {
        return work(seconds, luckShare, Econ.Work.TRADE);
    }

    Recipe stunt(double seconds, double luckShare) {
        return work(seconds, luckShare, Econ.Work.STUNT);
    }

    Recipe hazard(double perMinute) {
        hazard = perMinute;
        return this;
    }

    Recipe heavy() {
        heavy = true;
        return this;
    }

    Recipe night() {
        nightOnly = true;
        return this;
    }

    Recipe notBefore(double second) {
        notBefore = second;
        return this;
    }

    Recipe auto(Ms m) {
        auto = m.ordinal();
        return this;
    }

    Recipe autoRes(Res r, int n) {
        autoRes = r.ordinal();
        autoN = n;
        return this;
    }

    /** A resource goal: that many in hand, and it is claimed. */
    Recipe have(Res r, int n) {
        return use(r, n).autoRes(r, n);
    }

    /** Claimed by the way at this rate per minute at night or in caves (see {@link #chance}). */
    Recipe chance(double perMinute) {
        chance = perMinute;
        return this;
    }

    Recipe chance(double perMinute, Ms needs) {
        chance = perMinute;
        chanceNeeds = needs.ordinal();
        return this;
    }

    Recipe impossible() {
        impossible = true;
        bob = false;
        return this;
    }

    Recipe noBob() {
        bob = false;
        return this;
    }

    /** Whether the goal is only to be had in (or through) the Nether. */
    public boolean nether() {
        if (place != null && place.dim == 1 || (needs & Ms.NETHER_VISITED.bit) != 0 || auto == Ms.NETHER_VISITED.ordinal()) return true;
        for (int i = 0; i < uses.length; i += 2) if (Econ.RES_PLACE[uses[i]].dim == 1) return true;
        int closure = needs;
        for (Ms m : Ms.ALL) if ((needs & m.bit) != 0) closure |= Econ.MS_CLOSURE[m.ordinal()];
        for (Ms m : Ms.ALL) {
            if ((closure & m.bit) == 0) continue;
            int[] u = Econ.MS_USES[m.ordinal()];
            for (int i = 0; i < u.length; i += 2) if (Econ.RES_PLACE[u[i]].dim == 1) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return id;
    }
}
