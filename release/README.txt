========================================
Ultimate Chicken Horse - Client
========================================

SYSTEM REQUIREMENTS:
- Java 17 or higher
- Windows 10/11 (64-bit)
- Graphics card with DirectX support

IMPORTANT:
- Server must be running before starting client
- Default server: 127.0.0.1:12345

HOW TO RUN:
1. Make sure the game server is running
2. Double-click "run_client.bat"
3. Or run from command line: run_client.bat

GAME CONTROLS:
- A / Left Arrow  : Move Left (or move camera when spectating)
- D / Right Arrow : Move Right (or move camera when spectating)
- W / Up / Space  : Jump
- S / Down        : Crouch

GAME MODES:
- Create Public Room  : Anyone can join
- Create Private Room : Need 4-digit code to join
- Join Random Room    : Join any public room
- Join with Code      : Enter 4-digit room code

GAMEPLAY:
1. Select Phase - Choose an object from random selection
2. Place Phase  - Place your object on the map (drag, rotate with mouse wheel)
3. Race Phase   - Race to the finish line!

SPECTATE MODE:
- After finishing or dying, use A/D to move camera
- Watch other players until round ends
- Leaderboard shows when all players finish

OBJECTS:
- Normal Platform : Gray, basic platform
- Death Platform  : Red, die when standing on top (recover after 2s)
- Bounce Platform : Green, extra jump boost
- Moving Platform: Blue (horizontal) / Purple (vertical)
- Turret         : Yellow-Red gradient, shoots bullets
- Eraser         : Removes platforms in range

TROUBLESHOOTING:
- If game doesn't start, check Java version: java -version
- Connection refused? Make sure server is running first
- Graphics error? Update your graphics drivers
- Still not working? Try running as administrator

========================================
HOW TO CONNECT TO REMOTE SERVER:
========================================

METHOD 1: Using ngrok (Recommended for Internet play)

1. Server host runs: ngrok tcp 5000
2. ngrok will show: tcp://2.tcp.ngrok.io:12345
3. Edit server_config.txt and enter: 2.tcp.ngrok.io
4. Edit port_config.txt and enter: 12345
5. Run the game!

METHOD 2: Using Local Network

1. Find server's local IP (ipconfig)
2. Edit server_config.txt and enter the IP
3. Edit port_config.txt and enter: 5000
4. Make sure firewall allows port 5000

METHOD 3: Using Public IP

1. Configure port forwarding on router
2. Edit server_config.txt with public IP
3. Edit port_config.txt with forwarded port

========================================
