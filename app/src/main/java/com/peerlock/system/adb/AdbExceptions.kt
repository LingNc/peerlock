package com.peerlock.system.adb

open class AdbProtocolException(message: String) : java.io.IOException(message)

class AdbInvalidPairingCodeException : AdbProtocolException("Invalid pairing code")

open class AdbKeyException(message: String) : java.io.IOException(message)
