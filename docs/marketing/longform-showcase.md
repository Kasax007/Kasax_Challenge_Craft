# Long-form showcase (TikTok format, 1080x1920, English)

A 7:50 showcase of Challenge Craft for TikTok and YouTube, from the viewer's perspective: what the mod is, how a run and the progression work, a tour of the challenges, the casino, multiplayer, Bob, and the call to action "free on CurseForge – link in the description".

## Versions

| File | Content |
| --- | --- |
| `challenge_craft_showcase_captions_music.mp4` | Captions burned in, with music |
| `challenge_craft_showcase_captions_nomusic.mp4` | Captions burned in, voice and sound effects only (for your own music) |
| `challenge_craft_showcase_clean_music.mp4` / `_clean_nomusic.mp4` | The same without captions |
| `showcase.srt`, `showcase_voice.m4a` | Captions as a file, voice only |

## Sound

- **Narrator:** Kokoro TTS (open source), voice `am_michael`; the pitch matches the narrator of the shorts. One call per paragraph, every paragraph checked with Whisper.
- **Music** by Kevin MacLeod (incompetech.com), licensed under CC BY 4.0. The credit belongs in the description:
  - "Itty Bitty 8 Bit": intro, how it works, progression, outro;
  - "Overworld": the challenge tour;
  - "Hep Cats": the casino.
- The music is ducked about 14 dB under the voice and comes up in the pauses.

## Rebuilding

- Footage: the film harness (`src/film`), new shots in `$SP/film/long/` (scenes in `WorldScenes`, `MenuScenes`).
- Voice: `tts/bin/python scripts/film/long/tts_kokoro.py`.
- Cut, mix and render: `tts/bin/python scripts/film/long/showcase.py` (`--plan` only checks the timing).
- Edit plan: `showcase.py`; each beat starts on a phrase of the narration.

## Narration


### Cold open

You've seen these videos. Minecraft, but the floor is lava. Minecraft, but every chunk is made of one random block. Minecraft, but if you move on a red light, you die.  
They look like the most fun anyone has ever had in this game. And for a long time, the only way to play them was to watch somebody else do it.  
Not anymore. This is Challenge Craft, a free mod that puts fifty of those challenges into your own world, ready to play, alone or with your friends.  

### How it works

Here's how it works. When you create a new world, there's a new tab called Challenges. Every challenge is a card, and you can pick just one, or stack as many as you dare.  
Every combination gets a difficulty rating, and that rating decides your reward. A gentle twist pays a little. A truly brutal combination pays a lot.  
Then you play. The goal is the same as always: beat the Ender Dragon. A timer tracks your run, and every challenge shows you on screen exactly what it's doing.  
When the dragon falls, you get a run summary card with your time, your difficulty and your XP. You can save it as an image and share it right away.  

### Progression

And that XP is the clever part. It's Lifetime XP, which means it never goes away. It carries over into every new world and every server you play on.  
Lifetime XP fills a level tree from one to twenty. Every level unlocks new challenges, so you start with a handful and slowly work your way up to all fifty.  
Levels also unlock permanent perks, like night vision, extra hearts or extra strength, for every run you start from then on. And past level twenty, you collect Infinity Stars, which give your name a colored aura that everyone on the server can see.  

### The challenges

So what can you actually play? Let's take a look at some of the challenges, because this is where the mod really shines.  

### Movement

Red Light, Green Light turns the whole world into the famous game. While the light is green, you can run, mine and fight. When it turns yellow, you have three seconds. And on red, nothing is allowed to move. Not a step, not a jump, not even your camera.  
In Dice Movement, you throw a real die that tumbles across the ground. However many pips land face up, that's how many blocks you may walk, and a ring around you shows how far you can still go.  
And in Cushion Only, you can't walk at all. You place a cushion, you sit on it, and you stand up where it was. Every journey becomes a chain of little hops.  

### World

Some challenges change the world itself. With Random Chunk Blocks, every single chunk is rebuilt from one random block. One step you're walking on gold, the next on redstone, and the next on something completely different.  
Skyblock drops you on a tiny island in the void. And in The Floor is Lava, you start burning the moment you stand still for three seconds, or the moment you touch natural ground.  
Chunk Hunt is even stranger. Every time you enter a new chunk, the border locks around you, and you have to defeat a glowing target mob before you're allowed to leave.  

### Mobs and drops

Then there are the mob challenges. In Size Matters, every mob spawns at a random size. Tiny ones are fast, and giant ones have more health and a longer reach. And Double Trouble multiplies every spawn, up to ten times. One zombie is never just one zombie.  
Drops get the same treatment. With Upside Down Drops, everything you break floats up into the sky, so you'd better catch it fast. Random Block Drops makes every block drop something completely different, and Progressive Block Drops only unlocks a block once you've mined the target shown on your screen.  

### Game modes

And some challenges are complete game modes. In Force Item Battle, every player gets their own random target item, floating right above their head. Whoever collects the most items before the timer runs out wins.  
Lockout Bingo puts up to four teams on one shared five by five board. Every goal can only be claimed once, so every tile you take is a tile your rivals will never get.  
There's also All Biomes, where the whole server shares one checklist, Minecraft Trivia, where you answer a question every two minutes or die, and the ultimate grinds, All Items and All Entities.  

### The House Always Wins

But the challenge everyone talks about is number fifty, The House Always Wins. It turns your survival world into a casino.  
Every item you own is suddenly worth casino chips. You cash in at the dealer's booth at spawn, and he will happily buy your loot. But every ten minutes, the House collects a fee from the whole team, and that fee keeps climbing. If you can't pay, the House wins, and your run is over.  
So you can farm the money the slow way, or you can gamble. Mine Fever is a five reel slot machine with free spins and a top prize of five thousand times your bet. You can even bet a whole stack of items, and win it back as a fountain.  
There's Plinko, where you drop the ball and pray for the edges. There's Crash, where a rocket climbs and the multiplier grows, until you cash out, or it explodes. And there's a full roulette table, where you place your chips right on the felt.  
Of course, the House always wins in the end. Every game pays back ninety six percent on average, and every lost bet sends monsters after you. The bigger the loss, the nastier the wave.  
And if you die? You lose a quarter of your chips, and the dealer deals you a hand of Blackjack. Win, and you stand up right where you fell, with everything you had. Lose, and you're dead for real.  

### Play together

Every single challenge works in multiplayer. Levels and XP sync to every player, and name tags show everyone's level and aura.  
You can save your favorite setups as presets, or turn any run into a short challenge code, seed included. Send it to a friend, and they play the exact same world with the exact same rules. And every day there's a Daily Challenge, one ruleset and one seed for everyone, so you can come back tomorrow and beat your best time.  

### Meet Bob

And if nobody's online? Open the Lockout lobby and play against Bob. Bob is an AI player who plays the board just like you do. He reads the goals, plans a route, mines, crafts, fights, and even builds a Nether portal to claim his tiles.  
Bob is still in an early stage of development, so he makes mistakes, and sometimes he dies in very creative ways. But he learns new tricks with every update.  

### Outro

So whether you want to play your favorite YouTube challenge, race your friends, or find out how long you can survive against the House, there's a run waiting for you.  
Challenge Craft is completely free on CurseForge. The link is in the description. Pick a challenge, invite your friends, and see how far you can get.  
