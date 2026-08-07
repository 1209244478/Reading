package com.wrz.reading.dlna;

import org.fourthline.cling.android.AndroidUpnpServiceConfiguration;
import org.fourthline.cling.android.AndroidUpnpServiceImpl;

/**
 * 使用 CustomUpnpServiceConfiguration 的 AndroidUpnpServiceImpl，
 * 以 DOM 版描述符 binder 替换默认 SAX 版，解决 Android 上设备描述解析失败问题。
 */
public class CustomAndroidUpnpServiceImpl extends AndroidUpnpServiceImpl {
    @Override
    protected AndroidUpnpServiceConfiguration createConfiguration() {
        return new CustomUpnpServiceConfiguration();
    }
}
