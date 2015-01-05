# SmartThings HUB + Samsung SmartCam SNH-P6410BN integration with www digest authentication.

### Description

HUB API is not allowing for two physicalgraph.device.HubAction in a row in a quite short period of time (less than a minute). Digest web authentication needs that to get the token. As the workaround, there is a dedicated button to do the auth first, then after successful auth, button will change color to green and you will be able to take pictures. To pass data between buttons, I'm using special "state" variable. More details here: google smartapp-developers-guide/state.html.
 
### Setup
1. Go to: https://graph.api.smartthings.com/ide/devices and click on "New SmartDevice"
2. Add name (example: SmartCam), author and namespace (that's it). Click on "Create".
3. Edit it and paste this file.
4. Go to: https://graph.api.smartthings.com/device/list
5. Click on "New Device". Add Name, Device Network Id (put anything there), Type: SmartCam (same as you added in step 2 above),<br>
Version: Published, Location: your hub name
6. Immediately after adding the device, click on the "Edit" link next to the "Preferences" entry in the table.
7. Populate settings:
  a) Camera IP Address (192.168, 172.1x or 10.x.x.x works fine too)
  b) Camera Port (usually 80, but can be anything you like)
  c) Camera Path to Image, when it comes to SNH-P6410BN, use: "/cgi-bin/video.cgi?msubmenu=jpg&resolution=2".
     Note: with SNH-P6410BN you can modify resolution from 1 to 10.
  d) Does Camera use a Post or Get, normally Get? GET
  e) Camera User, for SNH-P6410BN it's admin
  f) Camera Password: anything you've set during your camera setup
8. Go to your mobile app and add a new widget.
9. Click on "Auth" button and see if you can sucessfully authenticate with your local camera. If not, triple check your settings.
Note: you can look at your logs in the real time if you're curious what's happening: https://graph.api.smartthings.com/ide/logs
 
Date: 2014-12-30
Version: 1.0 (tested and working)
 