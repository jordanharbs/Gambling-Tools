# DonutSMP Stream Manager

Client-side Fabric mod for Minecraft Java 26.1.2. Press **Right Shift** to open the manager.

## Features
- Watches incoming chat/system messages for payments matching `PlayerName paid you $250k` or `You received $250k from PlayerName`.
- Saves per-player received totals to `.minecraft/config/donutsmp-stream-manager.json`.
- Shows the latest detected payment in the HUD and menu.
- Set an auction item and start/reset a countdown (1–3600 seconds).
- Displays the highest bid and recent bids.
- Auto-detects simple bid chat lines while an auction is active, and also supports manual bidder + amount entry.
- End auction and toggle HUD.

## Build on GitHub
1. Create/open your GitHub repository.
2. Upload the contents of this folder, including `.github`.
3. Commit to `main` or `master`, or run the workflow manually from Actions.
4. Download the `DonutSMP-Stream-Manager` artifact from the successful workflow.
5. Put the normal mod JAR in `.minecraft/mods`.

## Important chat-format note
Minecraft servers can format payments and bids differently. The payment parser currently recognizes the two formats above. Automatic bid parsing is intentionally conservative and may need the exact DonutSMP bid announcement format to recognize every bid. Manual bid entry is included as a fallback. This mod only reads messages delivered to your client; it does not send payments or place bids.

## Dependencies
Minecraft 26.1.2, Fabric Loader 0.19.5+, Fabric API, Java 25.
