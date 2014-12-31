/**
 *  SmartCam SNH-P6410BN integration
 *
 *  Copyright 2014 Grzegorz Kowszewicz
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Date: 2014-12-30
 *  Version: 1.0 Stable (and tested)
 *
 */
metadata {
	definition (name: "SmartCam", namespace: "SmartCam/mojacamera", author: "Grzegorz Kowszewicz") {
		capability "Actuator"
		capability "Sensor"
		capability "Image Capture"
        
        command "setAuthToken"
        command "removeAuthToken"
	}

    preferences {
    input("CameraIP", "string", title:"Camera IP Address", description: "Please enter your camera's IP Address", required: true, displayDuringSetup: true)
    input("CameraPort", "string", title:"Camera Port", description: "Please enter your camera's Port", defaultValue: 80 , required: true, displayDuringSetup: true)
    input("CameraPath", "string", title:"Camera Path to Image", description: "Please enter the path to the image (default: /cgi-bin/video.cgi?msubmenu=jpg&resolution=2)", defaultValue: "/cgi-bin/video.cgi?msubmenu=jpg&resolution=2", required: true, displayDuringSetup: true)
    input("CameraAuth", "bool", title:"Does Camera require User Auth?", description: "Please choose if the camera requires authentication (only basic is supported)", defaultValue: true, displayDuringSetup: true)
    input("CameraPostGet", "string", title:"Does Camera use a Post or Get, normally Get?", description: "Please choose if the camera uses a POST or a GET command to retreive the image", defaultValue: "GET", displayDuringSetup: true)
    input("CameraUser", "string", title:"Camera User", description: "Please enter your camera's username (default: admin)", defaultValue: "admin", required: false, displayDuringSetup: true)
    input("CameraPassword", "string", title:"Camera Password", description: "Please enter your camera's password", required: false, displayDuringSetup: true)
	}
    
	simulator {
	}

	tiles {
		standardTile("camera", "device.image", width: 1, height: 1, canChangeIcon: false, inactiveLabel: true, canChangeBackground: true) {
			state "default", label: "", action: "", icon: "st.camera.dropcam-centered", backgroundColor: "#FFFFFF"
		}

		carouselTile("cameraDetails", "device.image", width: 3, height: 2) { }

		standardTile("take", "device.image", width: 1, height: 1, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false) {
			state "take", label: "Take", action: "Image Capture.take", icon: "st.camera.camera", backgroundColor: "#FFFFFF", nextState:"taking"
			state "taking", label:'Taking', action: "", icon: "st.camera.take-photo", backgroundColor: "#53a7c0"
			state "image", label: "Take", action: "Image Capture.take", icon: "st.camera.camera", backgroundColor: "#FFFFFF", nextState:"taking"
		}
        
        standardTile("authenticate", "device.button", width: 1, height: 1, canChangeIcon: true) {
            state "DeAuth", label: '${name}', action: "removeAuthToken", icon: "st.switches.light.on", backgroundColor: "#79b821"
            state "Auth", label: '${name}', action: "setAuthToken", icon: "st.switches.light.off", backgroundColor: "#ffffff"
        }
        
        main "camera"
		details(["cameraDetails", "take", "error", "authenticate"])
	}
}

def setAuthToken() {
 log.debug "setAuth"
 state.auth = "empty"
 take()
}

def removeAuthToken() {
 log.debug "removeAuth"
 sendEvent(name: "authenticate", value: "Auth")
 state.auth = "empty"
}

def parse(String output) {
    log.debug "Parsing output: '${output}'"
    def headers = ""
    def parsedHeaders = ""
    def map = stringToMap(output)

    if(map.headers) {
      headers = new String(map.headers.decodeBase64())
      parsedHeaders = parseHttpHeaders(headers)
    
      if (parsedHeaders.auth) {
        state.auth = parsedHeaders.auth
        log.debug "Got 401, send request again (click on 'take' one more time): " + state.auth
        sendEvent(name: "authenticate", value: "DeAuth")
        return result
      }
    }

    if (map.body != null) {
      def bodyString = new String(map.body.decodeBase64())
      log.debug bodyString
    }

    if (map.bucket && map.key) {
        log.debug "I've got the pic. Uploading to S3"
    	putImageInS3(map)
    }
    
    return result
}

private parseHttpHeaders(String headers) {
    def lines = headers.readLines()
    def status = lines[0].split()
    
    def result = [
        protocol:   status[0],
        status:     status[1].toInteger(),
        reason:     status[2]
    ]

    if (result.status == 401) {
        result.auth = stringToMap(lines[1].replaceAll("WWW-Authenticate: Digest ", "").replaceAll("=",":").replaceAll("\"", ""))
        log.debug "It's ok. Press take again" + result.auth
    }
    
    if (result.status == 200) {
       log.debug "Authentication successful! :" + result
    }
    
    return result
}

def putImageInS3(map) {
	def s3ObjectContent

	try {
		def imageBytes = getS3Object(map.bucket, map.key + ".jpg")

		if(imageBytes)
		{
			s3ObjectContent = imageBytes.getObjectContent()
			def bytes = new ByteArrayInputStream(s3ObjectContent.bytes)
			storeImage(getPictureName(), bytes)
		}
	}
	catch(Exception e) {
		log.error e
	}
	finally {
		if (s3ObjectContent) { s3ObjectContent.close() }
	}
}


def take() {
    def host = CameraIP
    def porthex = convertPortToHex(CameraPort)    
    def hosthex = convertIPtoHex(CameraIP)
    def path = CameraPath.trim()
    def request = ""
    device.deviceNetworkId = "$hosthex:$porthex"
    
    log.debug "The device id configured is: $device.deviceNetworkId" 
    log.debug "state: " + state

    if (!state.auth || state.auth == "empty") {
      request = """GET ${path} HTTP/1.1\r\nAccept: */*\r\nHost: ${getHostAddress()}\r\n\r\n"""
    } else {
      def auth_headers = calcDigestAuth(state.auth)
      request = """GET ${path} HTTP/1.1\r\nAccept: */*\r\nHost: ${getHostAddress()}\r\nAuthorization: ${auth_headers}\r\n\r\n"""
    }

    try {
      def hubAction = new physicalgraph.device.HubAction(request, physicalgraph.device.Protocol.LAN, "${device.deviceNetworkId}")
      if (state.auth && state.auth != "empty") {
        hubAction.options = [outputMsgToS3:true]
      }
      log.debug hubAction.getProperties()
      return hubAction
    }
    catch (Exception e) {
      log.debug "Hit Exception $e on $hubAction"
    }
}

private getPictureName() {
	def pictureUuid = java.util.UUID.randomUUID().toString().replaceAll('-', '')
	return device.deviceNetworkId + "_$pictureUuid" + ".jpg"
}

private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
    log.debug "IP address entered is $ipAddress and the converted hex code is $hex"
    return hex
}

private String convertPortToHex(port) {
	String hexport = port.toString().format( '%04x', port.toInteger() )
    log.debug hexport
    return hexport
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}


private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private getHostAddress() {
	def parts = device.deviceNetworkId.split(":")
	def ip = convertHexToIP(parts[0])
	def port = convertHexToInt(parts[1])
	return ip + ":" + port
}


private hashMD5(String somethingToHash) {
	java.security.MessageDigest.getInstance("MD5").digest(somethingToHash.getBytes("UTF-8")).encodeHex().toString()
}

private String calcDigestAuth(headers) {
	def HA1 = new String("${CameraUser}:" + headers.realm.trim() + ":${CameraPassword}").trim().encodeAsMD5()
	def HA2 = new String("${CameraPostGet}:${CameraPath}").trim().encodeAsMD5()

    if(!state.nc) {
      state.nc = 1
    } else {
      state.nc = state.nc + 1
    }

    def random = java.util.UUID.randomUUID().toString().replaceAll('-', '').substring(0, 8)
	def cnonce = random
    def response = new String("${HA1}:" + headers.nonce.trim() + ":" + state.nc + ":" + cnonce + ":" + "auth" + ":${HA2}")
    def response_enc = response.encodeAsMD5()

//    log.debug "HA1: " + HA1 + " ===== org:" + "${CameraUser}:" + headers.realm.trim() + ":${CameraPassword}"
//    log.debug "HA2: " + HA2 + " ===== org:" + "${CameraPostGet}:${CameraPath}"
//    log.debug "Response: " + response_enc + " =====   org:" + response

    def eol = " "
        
	return 'Digest username="' + CameraUser.trim() + '",' + eol +
           'realm="' + headers.realm.trim() + '",' + eol +
           'qop="' + headers.qop.trim() + '",' + eol +
           'algorithm="MD5",' + eol +
           'uri="'+ CameraPath.trim() + '",' +  eol +
           'nonce="' + headers.nonce.trim() + '",' + eol +
           'cnonce="' + cnonce.trim() + '",'.trim() + eol +
           'opaque="",' + eol +
           'nc=' + state.nc + ',' + eol +
           'response="' + response_enc.trim() + '"'
}

private def delayHubAction(ms) {
    return new physicalgraph.device.HubAction("delay ${ms}")
}

private getCallBackAddress() {
	device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP")
}
