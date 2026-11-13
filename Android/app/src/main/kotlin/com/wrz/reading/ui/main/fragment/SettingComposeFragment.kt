package com.wrz.reading.ui.main.fragment

import android.content.Context
import android.content.DialogInterface
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.wrz.reading.R
import com.wrz.reading.app.MyApplication
import com.wrz.reading.common.BaseFragment
import com.wrz.reading.common.LocalNetworkPermission
import com.wrz.reading.ui.compose.theme.ReadingComposeTheme
import com.wrz.reading.ui.main.activity.DownloadListActivity
import com.wrz.reading.ui.main.compose.SettingScreen
import com.wrz.reading.ui.main.compose.receiveModeLabelRes
import com.wrz.reading.ui.main.utils.DialogHelper
import com.wrz.reading.ui.read.dlna.DlnaRendererManager
import com.wrz.reading.ui.read.dlna.DlnaRendererManager.ReceiveMode

/**
 * 设置页 Compose 实现。
 *
 * 保留原逻辑：
 * - 接收投屏开关：先同步状态，用户切换后写入并启停 DLNA 接收端；
 * - 接收方式：单选弹窗、协议支持校验、切换后立即重启接收端；
 * - 本设备名称：文本输入弹窗、保存并在接收中重启；
 * - 下载列表跳转；
 * - 原布局中整行 gone 的“退出阅读后清理”不渲染。
 */
class SettingComposeFragment : BaseFragment() {

    private var receiveEnabled by mutableStateOf(false)
    private var receiveMode by mutableStateOf(ReceiveMode.DLNA)
    private var castName by mutableStateOf("")
    /** 本次页面内是否已发起本地网络权限请求（避免 initData 重复弹窗） */
    private var localNetworkRequested = false

    override fun getLayoutResId(): Int = R.layout.fragment_setting_compose

    override fun initView() {
        val composeView = findId<ComposeView>(R.id.compose_view) ?: return
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent {
            ReadingComposeTheme {
                SettingScreen(
                    receiveEnabled = receiveEnabled,
                    receiveMode = receiveMode,
                    castName = castName,
                    onReceiveEnabledChange = ::onReceiveEnabledChange,
                    onReceiveModeClick = ::showReceiveModeDialog,
                    onNameClick = ::showCastNameDialog,
                    onDownloadClick = ::openDownloadList
                )
            }
        }
    }

    override fun initData() {
        val ctx = context ?: return
        castName = MyApplication.manager.castName.orEmpty()
        receiveEnabled = DlnaRendererManager.isReceiverEnabled(ctx)
        receiveMode = DlnaRendererManager.getReceiveMode(ctx)
        // 已开启接收但缺少本地网络权限（如升级到 Android 16+）：进入设置页时补授权
        if (receiveEnabled && !LocalNetworkPermission.isGranted(ctx) && !localNetworkRequested) {
            localNetworkRequested = true
            requestPermissions(
                arrayOf(LocalNetworkPermission.PERMISSION),
                LocalNetworkPermission.REQUEST_CODE
            )
        }
    }

    override fun configViews() {
        // Compose 随状态自动刷新
    }

    override fun goBack() {
    }

    private fun onReceiveEnabledChange(checked: Boolean) {
        val ctx = context ?: return
        if (!checked) {
            receiveEnabled = false
            DlnaRendererManager.setReceiverEnabled(ctx, false)
            DlnaRendererManager.getInstance().stop()
            return
        }
        // Android 16+：调度本地网络权限，授权成功后再真正启动接收端
        if (LocalNetworkPermission.isGranted(ctx)) {
            enableReceiver(ctx)
            return
        }
        receiveEnabled = true
        localNetworkRequested = true
        requestPermissions(
            arrayOf(LocalNetworkPermission.PERMISSION),
            LocalNetworkPermission.REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != LocalNetworkPermission.REQUEST_CODE) {
            return
        }
        val ctx = context ?: return
        if (LocalNetworkPermission.isGranted(ctx)) {
            enableReceiver(ctx)
        } else {
            // 未授权：回退开关并持久化关闭，避免下次启动时静默启动失败
            receiveEnabled = false
            DlnaRendererManager.setReceiverEnabled(ctx, false)
            DlnaRendererManager.getInstance().stop()
            Toast.makeText(
                ctx,
                R.string.msg_local_network_permission_required,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun enableReceiver(ctx: Context) {
        receiveEnabled = true
        DlnaRendererManager.setReceiverEnabled(ctx, true)
        DlnaRendererManager.getInstance().start(ctx)
    }

    /** 接收方式单选弹窗：与原实现一致的协议支持校验和重启行为。 */
    private fun showReceiveModeDialog() {
        val ctx = context ?: return
        val modes = arrayOf(
            ReceiveMode.DLNA,
            ReceiveMode.WRZCAST,
            ReceiveMode.AIRPLAY,
            ReceiveMode.CHROMECAST,
            ReceiveMode.ALL
        )
        val labels = Array(modes.size) { getReceiveModeLabel(modes[it]) }
        val current = DlnaRendererManager.getReceiveMode(ctx)
        var checked = 0
        for (i in modes.indices) {
            if (modes[i] == current) {
                checked = i
            }
        }

        AlertDialog.Builder(ctx)
            .setTitle(R.string.title_receive_mode)
            .setSingleChoiceItems(labels, checked) { dialog: DialogInterface, which: Int ->
                dialog.dismiss()
                val mode = modes[which]
                if (mode == DlnaRendererManager.getReceiveMode(ctx)) {
                    return@setSingleChoiceItems
                }

                val chromecastSupported =
                    DlnaRendererManager.isChromecastReceiverSupported(ctx)
                val airPlaySupported = DlnaRendererManager.isAirPlayReceiverSupported(ctx)

                if (mode == ReceiveMode.CHROMECAST && !chromecastSupported) {
                    Toast.makeText(
                        ctx,
                        R.string.msg_chromecast_not_supported,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setSingleChoiceItems
                }
                if (mode == ReceiveMode.AIRPLAY && !airPlaySupported) {
                    Toast.makeText(
                        ctx,
                        R.string.msg_airplay_not_supported,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setSingleChoiceItems
                }

                DlnaRendererManager.setReceiveMode(ctx, mode)
                receiveMode = mode

                if ((mode.includesChromecast() && !chromecastSupported) ||
                    (mode.includesAirPlay() && !airPlaySupported)
                ) {
                    Toast.makeText(
                        ctx,
                        R.string.msg_receive_mode_unsupported_protocol,
                        Toast.LENGTH_SHORT
                    ).show()
                }

                if (receiveEnabled) {
                    DlnaRendererManager.getInstance().reStart(MyApplication.app)
                }
            }
            .show()
    }

    private fun showCastNameDialog() {
        val activity = activity ?: return
        DialogHelper.showTextInputDialog(
            activity,
            getString(R.string.title_set_cast_name),
            "",
            getString(R.string.btn_save),
            castName
        ) { text ->
            castName = text
            MyApplication.manager.saveCastName(text)
            if (receiveEnabled) {
                DlnaRendererManager.getInstance().reStart(MyApplication.app)
            }
        }
    }

    private fun openDownloadList() {
        val ctx = context ?: return
        DownloadListActivity.start(ctx)
    }

    private fun getReceiveModeLabel(mode: ReceiveMode): String =
        getString(receiveModeLabelRes(mode))
}
