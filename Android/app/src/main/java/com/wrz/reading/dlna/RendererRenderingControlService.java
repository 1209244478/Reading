package com.wrz.reading.dlna;

import org.fourthline.cling.binding.annotations.UpnpAction;
import org.fourthline.cling.binding.annotations.UpnpInputArgument;
import org.fourthline.cling.binding.annotations.UpnpOutputArgument;
import org.fourthline.cling.binding.annotations.UpnpService;
import org.fourthline.cling.binding.annotations.UpnpServiceId;
import org.fourthline.cling.binding.annotations.UpnpServiceType;
import org.fourthline.cling.binding.annotations.UpnpStateVariable;
import org.fourthline.cling.binding.annotations.UpnpStateVariables;
import org.fourthline.cling.model.types.UnsignedIntegerFourBytes;
import org.fourthline.cling.model.types.UnsignedIntegerTwoBytes;

/**
 * DLNA MediaRenderer 的 RenderingControl 服务（自实现注解版）。
 *
 * 音量/静音由系统音量控制，此处为满足协议契约返回固定值：音量 100、未静音。
 * 预设列表仅返回 FactoryDefaults。所有状态变量 sendEvents=false（无访问器）。
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@UpnpService(
        serviceId = @UpnpServiceId("RenderingControl"),
        serviceType = @UpnpServiceType(value = "RenderingControl", version = 1)
)
@UpnpStateVariables({
        @UpnpStateVariable(name = "A_ARG_TYPE_InstanceID", datatype = "ui4", sendEvents = false),
        @UpnpStateVariable(name = "A_ARG_TYPE_Channel", datatype = "string",
                allowedValues = {"Master"}, sendEvents = false),
        @UpnpStateVariable(name = "Volume", datatype = "ui2", sendEvents = false),
        @UpnpStateVariable(name = "Mute", datatype = "boolean", sendEvents = false),
        @UpnpStateVariable(name = "PresetNameList", datatype = "string", sendEvents = false)
})
public class RendererRenderingControlService {

    @UpnpAction(name = "GetVolume",
            out = @UpnpOutputArgument(name = "CurrentVolume", stateVariable = "Volume"))
    public UnsignedIntegerTwoBytes getVolume(
            @UpnpInputArgument(name = "InstanceID", stateVariable = "A_ARG_TYPE_InstanceID")
                    UnsignedIntegerFourBytes instanceId,
            @UpnpInputArgument(name = "Channel", stateVariable = "A_ARG_TYPE_Channel")
                    String channel) {
        // 音量由系统控制，返回固定值 100 满足协议
        return new UnsignedIntegerTwoBytes(100);
    }

    @UpnpAction(name = "SetVolume")
    public void setVolume(
            @UpnpInputArgument(name = "InstanceID", stateVariable = "A_ARG_TYPE_InstanceID")
                    UnsignedIntegerFourBytes instanceId,
            @UpnpInputArgument(name = "Channel", stateVariable = "A_ARG_TYPE_Channel")
                    String channel,
            @UpnpInputArgument(name = "DesiredVolume", stateVariable = "Volume")
                    UnsignedIntegerTwoBytes desiredVolume) {
        // 不实际改变音量，交由系统音量键控制
    }

    @UpnpAction(name = "GetMute",
            out = @UpnpOutputArgument(name = "CurrentMute", stateVariable = "Mute"))
    public Boolean getMute(
            @UpnpInputArgument(name = "InstanceID", stateVariable = "A_ARG_TYPE_InstanceID")
                    UnsignedIntegerFourBytes instanceId,
            @UpnpInputArgument(name = "Channel", stateVariable = "A_ARG_TYPE_Channel")
                    String channel) {
        return Boolean.FALSE;
    }

    @UpnpAction(name = "SetMute")
    public void setMute(
            @UpnpInputArgument(name = "InstanceID", stateVariable = "A_ARG_TYPE_InstanceID")
                    UnsignedIntegerFourBytes instanceId,
            @UpnpInputArgument(name = "Channel", stateVariable = "A_ARG_TYPE_Channel")
                    String channel,
            @UpnpInputArgument(name = "DesiredMute", stateVariable = "Mute")
                    Boolean desiredMute) {
        // 不实际改变静音状态
    }

    @UpnpAction(name = "ListPresets",
            out = @UpnpOutputArgument(name = "CurrentPresetNameList", stateVariable = "PresetNameList"))
    public String listPresets(
            @UpnpInputArgument(name = "InstanceID", stateVariable = "A_ARG_TYPE_InstanceID")
                    UnsignedIntegerFourBytes instanceId) {
        return "FactoryDefaults";
    }
}
