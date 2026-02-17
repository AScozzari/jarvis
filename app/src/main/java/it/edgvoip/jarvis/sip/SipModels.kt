package it.edgvoip.jarvis.sip

enum class RegistrationState {
    UNREGISTERED,
    REGISTERING,
    REGISTERED,
    UNREGISTERING,
    FAILED
}

enum class CallState {
    IDLE,
    CALLING,
    RINGING,
    INCOMING,
    CONNECTED,
    HOLDING,
    DISCONNECTED
}

enum class CallDirection {
    INCOMING,
    OUTGOING
}

data class CallInfo(
    val id: String,
    val number: String,
    val name: String = "",
    val state: CallState = CallState.IDLE,
    val duration: Int = 0,
    val direction: CallDirection = CallDirection.OUTGOING
)
