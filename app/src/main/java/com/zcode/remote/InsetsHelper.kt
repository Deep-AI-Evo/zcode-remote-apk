package com.zcode.remote

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 系统栏 + 输入法 insets 统一处理：
 * - 内容完整落在状态栏/导航栏下方（Android 15 强制 edge-to-edge 下尤其必要，避免与状态栏重叠导致点击被吃）
 * - 键盘弹起时把页面顶起，输入框不被输入法遮挡
 */
object InsetsHelper {

    fun apply(root: View, handleIme: Boolean = false) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottom = if (handleIme) maxOf(bars.bottom, ime.bottom) else bars.bottom
            v.setPadding(bars.left, bars.top, bars.right, bottom)
            WindowInsetsCompat.CONSUMED
        }
    }
}