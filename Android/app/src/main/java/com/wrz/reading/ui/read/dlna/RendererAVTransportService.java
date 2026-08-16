package com.wrz.reading.ui.read.dlna;

import org.fourthline.cling.binding.annotations.UpnpAction;
import org.fourthline.cling.binding.annotations.UpnpInputArgument;
import org.fourthline.cling.binding.annotations.UpnpOutputArgument;
import org.fourthline.cling.binding.annotations.UpnpService;
import org.fourthline.cling.binding.annotations.UpnpServiceId;
import org.fourthline.cling.binding.annotations.UpnpServiceType;
import org.fourthline.cling.binding.annotations.UpnpStateVariable;
import org.fourthline.cling.binding.annotations.UpnpStateVariables;
import org.fourthline.cling.model.types.UnsignedIntegerFourBytes;

/**
 * DLNA MediaRenderer 的 AVTransport 服务（自实现注解版，未继承 cling-support 的状态机）。
 *
 * 所有动作委托 {@link DlnaRendererManager} 处理。状态变量均声明为 sendEvents=false：
 * 因为这些变量仅通过类级 {@code @UpnpStateVariables} 声明，没有对应的字段/getter 访问器，
 * 若 sendEvents=true，Cling 的 AnnotationStateVariableBinder 会因找不到访问器而抛出绑定异常。
 * 因此不提供 GENA 事件订阅，控制点通过 GetPositionInfo/GetTransportInfo 主动轮询即可。
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@UpnpService(
        serviceId = @UpnpServiceId("AVTransport"),
        serviceType = @UpnpServiceType(value = "AVTransport", version = 1)
)
@UpnpStateVariables({
        @UpnpStateVariable(name = "A_ARG_TYPE_InstanceID", datatype = "ui4", sendEvents = false),
        @UpnpStateVariable(name = "AVTransportURI", datatype = "string", sendEvents = false),
        @UpnpStateVariable(name = "AVTransportURIMetaData", datatype = "string", sendEvents = false),
        @UpnpStateVariable(name = "TransportPlaySpeed", datatype = "string",
                allowedValues = {"1"}, sendEvents = false),
        @UpnpStateVariable(name = "A_ARG_TYPE_SeekMode", datatype = "string",
                allowedValues = {"REL_TIME", "TRACK_NR"}, sendEvents = false),
        @UpnpStateVariable(name = "A_ARG_TYPE_SeekTarget", datatype = "string", sendEvents = false),
        @UpnpStateVariable(name = "CurrentTrack", datatype = "ui4", sendEvents = false),
        @UpnpStateVariable(name = "CurrentTrackDuration", datatype = "string", sendEvents = false),
        @UpnpStateVariable(name = "CurrentTrackMetaData", datatype = "string", sendEvents = false),
        @UpnpStateVariable(name = "CurrentTrackURI", datatype = "string", sendEvents = false),
        @UpnpStateVariable(name = "RelativeTimePosition", datatype = "string", sendEvents = false),
        @UpnpStateVariable(name = "AbsoluteTimePosition", datatype = "string", sendEvents = false),
        @UpnpStateVariable(name = "RelativeCounterPosition", datatype = "ui4", sendEvents = false),
        @UpnpStateVariable(name = "AbsoluteCounterPosition", datatype = "ui4", sendEvents = false),
        @UpnpStateVariable(name = "TransportState", datatype = "string",
                allowedValues = {"STOPPED", "PLAYING", "PAUSED_PLAYBACK", "TRANSITIONING", "NO_MEDIA_PRESENT"},
                sendEvents = false),
        @UpnpStateVariable(name = "TransportStatus", datatype = "string",
                allowedValues = {"OK", "ERROR_OCCURRED"}, sendEvents = false),
        @UpnpStateVariable(name = "NumberOfTracks", datatype = "ui4", sendEvents = false),
        @UpnpStateVariable(name = "CurrentMediaDuration", datatype = "string", sendEvents = false),
        @UpnpStateVariable(name = "NextAVTransportURI", datatype = "string", sendEvents = false),
        @UpnpStateVariable(name = "NextAVTransportURIMetaData", datatype = "string", sendEvents = false),
        @UpnpStateVariable(name = "PlaybackStorageMedium", datatype = "string",
                allowedValues = {"NONE", "NETWORK"}, sendEvents = false),
        @UpnpStateVariable(name = "RecordStorageMedium", datatype = "string",
                allowedValues = {"NOT_IMPLEMENTED"}, sendEvents = false),
        @UpnpStateVariable(name = "RecordMediumWriteStatus", datatype = "string",
                allowedValues = {"NOT_IMPLEMENTED"}, sendEvents = false)
})
public class RendererAVTransportService {

    @UpnpAction(name = "SetAVTransportURI")
    public void setAVTransportURI(
            @UpnpInputArgument(name = "InstanceID", stateVariable = "A_ARG_TYPE_InstanceID")
                    UnsignedIntegerFourBytes instanceId,
            @UpnpInputArgument(name = "CurrentURI", stateVariable = "AVTransportURI")
                    String currentUri,
            @UpnpInputArgument(name = "CurrentURIMetaData", stateVariable = "AVTransportURIMetaData")
                    String currentUriMetaData) {
        DlnaRendererManager.getInstance().onSetUri(currentUri, currentUriMetaData);
    }

    @UpnpAction(name = "Play")
    public void play(
            @UpnpInputArgument(name = "InstanceID", stateVariable = "A_ARG_TYPE_InstanceID")
                    UnsignedIntegerFourBytes instanceId,
            @UpnpInputArgument(name = "Speed", stateVariable = "TransportPlaySpeed")
                    String speed) {
        DlnaRendererManager.getInstance().onPlay();
    }

    @UpnpAction(name = "Pause")
    public void pause(
            @UpnpInputArgument(name = "InstanceID", stateVariable = "A_ARG_TYPE_InstanceID")
                    UnsignedIntegerFourBytes instanceId) {
        DlnaRendererManager.getInstance().onPause();
    }

    @UpnpAction(name = "Stop")
    public void stop(
            @UpnpInputArgument(name = "InstanceID", stateVariable = "A_ARG_TYPE_InstanceID")
                    UnsignedIntegerFourBytes instanceId) {
        DlnaRendererManager.getInstance().onStop();
    }

    @UpnpAction(name = "Seek")
    public void seek(
            @UpnpInputArgument(name = "InstanceID", stateVariable = "A_ARG_TYPE_InstanceID")
                    UnsignedIntegerFourBytes instanceId,
            @UpnpInputArgument(name = "Unit", stateVariable = "A_ARG_TYPE_SeekMode")
                    String unit,
            @UpnpInputArgument(name = "Target", stateVariable = "A_ARG_TYPE_SeekTarget")
                    String target) {
        DlnaRendererManager.getInstance().onSeek(unit, target);
    }

    @UpnpAction(name = "GetPositionInfo", out = {
            @UpnpOutputArgument(name = "Track", stateVariable = "CurrentTrack", getterName = "getTrack"),
            @UpnpOutputArgument(name = "TrackDuration", stateVariable = "CurrentTrackDuration", getterName = "getTrackDuration"),
            @UpnpOutputArgument(name = "TrackMetaData", stateVariable = "CurrentTrackMetaData", getterName = "getTrackMetaData"),
            @UpnpOutputArgument(name = "TrackURI", stateVariable = "CurrentTrackURI", getterName = "getTrackURI"),
            @UpnpOutputArgument(name = "RelTime", stateVariable = "RelativeTimePosition", getterName = "getRelTime"),
            @UpnpOutputArgument(name = "AbsTime", stateVariable = "AbsoluteTimePosition", getterName = "getAbsTime"),
            @UpnpOutputArgument(name = "RelCount", stateVariable = "RelativeCounterPosition", getterName = "getRelCount"),
            @UpnpOutputArgument(name = "AbsCount", stateVariable = "AbsoluteCounterPosition", getterName = "getAbsCount")
    })
    public PositionInfoResponse getPositionInfo(
            @UpnpInputArgument(name = "InstanceID", stateVariable = "A_ARG_TYPE_InstanceID")
                    UnsignedIntegerFourBytes instanceId) {
        return DlnaRendererManager.getInstance().getPositionInfo();
    }

    @UpnpAction(name = "GetTransportInfo", out = {
            @UpnpOutputArgument(name = "CurrentTransportState", stateVariable = "TransportState", getterName = "getCurrentTransportState"),
            @UpnpOutputArgument(name = "CurrentTransportStatus", stateVariable = "TransportStatus", getterName = "getCurrentTransportStatus"),
            @UpnpOutputArgument(name = "CurrentSpeed", stateVariable = "TransportPlaySpeed", getterName = "getCurrentSpeed")
    })
    public TransportInfoResponse getTransportInfo(
            @UpnpInputArgument(name = "InstanceID", stateVariable = "A_ARG_TYPE_InstanceID")
                    UnsignedIntegerFourBytes instanceId) {
        return DlnaRendererManager.getInstance().getTransportInfo();
    }

    @UpnpAction(name = "GetMediaInfo", out = {
            @UpnpOutputArgument(name = "NrTracks", stateVariable = "NumberOfTracks", getterName = "getNrTracks"),
            @UpnpOutputArgument(name = "MediaDuration", stateVariable = "CurrentMediaDuration", getterName = "getMediaDuration"),
            @UpnpOutputArgument(name = "CurrentURI", stateVariable = "AVTransportURI", getterName = "getCurrentURI"),
            @UpnpOutputArgument(name = "CurrentURIMetaData", stateVariable = "AVTransportURIMetaData", getterName = "getCurrentURIMetaData"),
            @UpnpOutputArgument(name = "NextURI", stateVariable = "NextAVTransportURI", getterName = "getNextURI"),
            @UpnpOutputArgument(name = "NextURIMetaData", stateVariable = "NextAVTransportURIMetaData", getterName = "getNextURIMetaData"),
            @UpnpOutputArgument(name = "PlayMedium", stateVariable = "PlaybackStorageMedium", getterName = "getPlayMedium"),
            @UpnpOutputArgument(name = "RecordMedium", stateVariable = "RecordStorageMedium", getterName = "getRecordMedium"),
            @UpnpOutputArgument(name = "WriteStatus", stateVariable = "RecordMediumWriteStatus", getterName = "getWriteStatus")
    })
    public MediaInfoResponse getMediaInfo(
            @UpnpInputArgument(name = "InstanceID", stateVariable = "A_ARG_TYPE_InstanceID")
                    UnsignedIntegerFourBytes instanceId) {
        return DlnaRendererManager.getInstance().getMediaInfo();
    }

    // ===== 响应 Bean（公共静态内部类，供 Cling 反射调用 getter） =====

    /** GetPositionInfo 响应。ui4 字段使用 UnsignedIntegerFourBytes，保证类型校验通过。 */
    public static class PositionInfoResponse {
        private final UnsignedIntegerFourBytes track;
        private final String trackDuration;
        private final String trackMetaData;
        private final String trackURI;
        private final String relTime;
        private final String absTime;
        private final UnsignedIntegerFourBytes relCount;
        private final UnsignedIntegerFourBytes absCount;

        public PositionInfoResponse(UnsignedIntegerFourBytes track, String trackDuration,
                                    String trackMetaData, String trackURI,
                                    String relTime, String absTime,
                                    UnsignedIntegerFourBytes relCount, UnsignedIntegerFourBytes absCount) {
            this.track = track;
            this.trackDuration = trackDuration;
            this.trackMetaData = trackMetaData;
            this.trackURI = trackURI;
            this.relTime = relTime;
            this.absTime = absTime;
            this.relCount = relCount;
            this.absCount = absCount;
        }

        public UnsignedIntegerFourBytes getTrack() { return track; }
        public String getTrackDuration() { return trackDuration; }
        public String getTrackMetaData() { return trackMetaData; }
        public String getTrackURI() { return trackURI; }
        public String getRelTime() { return relTime; }
        public String getAbsTime() { return absTime; }
        public UnsignedIntegerFourBytes getRelCount() { return relCount; }
        public UnsignedIntegerFourBytes getAbsCount() { return absCount; }
    }

    /** GetTransportInfo 响应。 */
    public static class TransportInfoResponse {
        private final String currentTransportState;
        private final String currentTransportStatus;
        private final String currentSpeed;

        public TransportInfoResponse(String currentTransportState,
                                     String currentTransportStatus, String currentSpeed) {
            this.currentTransportState = currentTransportState;
            this.currentTransportStatus = currentTransportStatus;
            this.currentSpeed = currentSpeed;
        }

        public String getCurrentTransportState() { return currentTransportState; }
        public String getCurrentTransportStatus() { return currentTransportStatus; }
        public String getCurrentSpeed() { return currentSpeed; }
    }

    /** GetMediaInfo 响应。 */
    public static class MediaInfoResponse {
        private final UnsignedIntegerFourBytes nrTracks;
        private final String mediaDuration;
        private final String currentURI;
        private final String currentURIMetaData;
        private final String nextURI;
        private final String nextURIMetaData;
        private final String playMedium;
        private final String recordMedium;
        private final String writeStatus;

        public MediaInfoResponse(UnsignedIntegerFourBytes nrTracks, String mediaDuration,
                                 String currentURI, String currentURIMetaData,
                                 String nextURI, String nextURIMetaData,
                                 String playMedium, String recordMedium, String writeStatus) {
            this.nrTracks = nrTracks;
            this.mediaDuration = mediaDuration;
            this.currentURI = currentURI;
            this.currentURIMetaData = currentURIMetaData;
            this.nextURI = nextURI;
            this.nextURIMetaData = nextURIMetaData;
            this.playMedium = playMedium;
            this.recordMedium = recordMedium;
            this.writeStatus = writeStatus;
        }

        public UnsignedIntegerFourBytes getNrTracks() { return nrTracks; }
        public String getMediaDuration() { return mediaDuration; }
        public String getCurrentURI() { return currentURI; }
        public String getCurrentURIMetaData() { return currentURIMetaData; }
        public String getNextURI() { return nextURI; }
        public String getNextURIMetaData() { return nextURIMetaData; }
        public String getPlayMedium() { return playMedium; }
        public String getRecordMedium() { return recordMedium; }
        public String getWriteStatus() { return writeStatus; }
    }
}
