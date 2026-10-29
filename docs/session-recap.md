# 会话回顾

会话列表在标题下最多显示两行服务端 `recap`。聊天页重新进入或 Activity
回到前台时刷新会话元数据，在输入框上方显示同一句回顾；点击菱形标题行可以
收起或展开，正在回复时隐藏。中、英、日、韩文回顾使用对应语言标签。

`RuntimeSession` 和 `SessionHistory` 支持可选 `recap` / `recap_updated_at`，
兼容不返回这些字段的旧服务端。App 不生成、翻译回顾，也不把它保存为第二份
权威数据；只保留当前进程从服务端读取的展示状态。冷启动离线时没有回顾也不
从本地聊天历史推算。后台生成可能尚未完成，再次进入或刷新会获取已保存版本。

回顾独立于标题、compact summary、记忆和 RAG，依赖 lucky-agent issue #349
对应的 runtime / Agent / app PR；只更新 Android 无法产生回顾。
