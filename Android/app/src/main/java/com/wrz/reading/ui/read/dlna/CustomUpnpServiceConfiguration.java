package com.wrz.reading.ui.read.dlna;

import org.fourthline.cling.android.AndroidUpnpServiceConfiguration;
import org.fourthline.cling.binding.xml.DeviceDescriptorBinder;
import org.fourthline.cling.binding.xml.ServiceDescriptorBinder;
import org.fourthline.cling.binding.xml.UDA10DeviceDescriptorBinderImpl;
import org.fourthline.cling.binding.xml.UDA10ServiceDescriptorBinderImpl;

/**
 * 自定义 Cling 配置：用 DOM 版描述符 binder 替换默认的 SAX 版。
 *
 * 默认 SAX 版（UDA10*DescriptorBinderSAXImpl）依赖 seamless 的 SAXParser，
 * 它会设置 disallow-doctype-decl 等 Android 不支持的安全 feature，
 * 导致设备描述 XML 解析抛 SAXNotRecognizedException、设备被丢弃。
 *
 * DOM 版（UDA10*DescriptorBinderImpl）用 DocumentBuilderFactory 且不设置这些 feature，
 * 在 Android 上可正常解析。
 */
public class CustomUpnpServiceConfiguration extends AndroidUpnpServiceConfiguration {
    @Override
    protected ServiceDescriptorBinder createServiceDescriptorBinderUDA10() {
        return new UDA10ServiceDescriptorBinderImpl();
    }

    @Override
    protected DeviceDescriptorBinder createDeviceDescriptorBinderUDA10() {
        return new UDA10DeviceDescriptorBinderImpl();
    }
}
