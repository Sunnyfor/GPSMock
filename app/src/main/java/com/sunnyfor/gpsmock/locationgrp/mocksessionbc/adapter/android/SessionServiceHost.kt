package com.sunnyfor.gpsmock.locationgrp.mocksessionbc.adapter.android

import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.MockSession
import kotlinx.coroutines.CompletableDeferred

/** 框架服务的装配桥，不拥有第二套业务状态；仅由 Application wiring 初始化。 */
internal object SessionServiceHost {
    /** 唯一公开会话能力，服务与页面观察同一对象。 */
    lateinit var session: MockSession

    /** 服务已进入前台的本次确认；服务启动失败须异常完成。 */
    var ready: CompletableDeferred<Unit>? = null

    /** 本次销毁由已确认清理触发；否则服务销毁请求停止。 */
    var expectedStop = false
}
