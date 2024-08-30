package com.tans.tlocalvideochat.webrtc

import com.tans.tlocalvideochat.AppLog
import org.webrtc.CandidatePairChangeEvent
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver

interface SimplifyPeerConnectionObserver : PeerConnection.Observer {

    override fun onIceCandidate(ice: IceCandidate?) {
        AppLog.d(TAG, "Local ICE updated: sdpMid=${ice?.sdpMid}, sdpMLineIndex=${ice?.sdpMLineIndex}, sdp=${ice?.sdp}")
    }

    override fun onTrack(transceiver: RtpTransceiver?) {
        val track = transceiver?.receiver?.track()
        if (track != null) {
            if (track.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                AppLog.d(TAG, "Receive remote video track.")
            }
            if (track.kind() == MediaStreamTrack.AUDIO_TRACK_KIND) {
                AppLog.d(TAG, "Receive remote audio track.")
            }
        }
    }

    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
        AppLog.d(TAG, "onConnectionChange: $newState")
    }

    override fun onStandardizedIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
        AppLog.d(TAG, "onStandardizedIceConnectionChange: $newState")
    }

    override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent?) {
        AppLog.d(TAG, "onSelectedCandidatePairChanged: $event")
    }

    override fun onDataChannel(p0: DataChannel?) {
        AppLog.d(TAG, "onDataChannel: $p0")
    }

    override fun onAddStream(p0: MediaStream?) {
        AppLog.d(TAG, "onAddStream: $p0")
    }

    override fun onRemoveStream(p0: MediaStream?) {
        AppLog.d(TAG, "onRemoveStream: $p0")
    }

    override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
        AppLog.d(TAG, "onAddTrack: receiver=${receiver}, mediaStreams=${mediaStreams}")
        mediaStreams?.apply {
            for (stream in this) {
                for (track in stream.audioTracks) {
                    track.setEnabled(true)
                }
            }
        }
    }

    override fun onRemoveTrack(receiver: RtpReceiver?) {
        AppLog.d(TAG, "onRemoveTrack: $receiver")
    }


    override fun onRenegotiationNeeded() {
        AppLog.d(TAG, "onRenegotiationNeeded")
    }

    override fun onIceConnectionChange(iceState: PeerConnection.IceConnectionState?) {
        AppLog.d(TAG, "onIceConnectionChange: $iceState")
    }

    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
        AppLog.d(TAG, "onSignalingChange: $p0")
    }

    override fun onIceConnectionReceivingChange(p0: Boolean) {
        AppLog.d(TAG, "onIceConnectionReceivingChange: $p0")
    }

    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
        AppLog.d(TAG, "onIceGatheringChange: $p0")
    }

    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
        AppLog.d(TAG, "onIceCandidatesRemoved： $p0")
    }

    companion object {
        private const val TAG = "SimplifyPeerConnectionObserver"
    }
}