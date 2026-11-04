# 재시도 기록

<!-- 설계 역할(design-dispatch) 재시도도 이 표에 엔진이 기록: 티켓 칸 = `<역할>:<파트>`, 원인 = 이전 dispatch 상태 -->

| 일시 | 티켓 | 모델 | effort | 원인·로그 경로 | task ID | dispatch ID |
|---|---|---|---|---|---|---|
| 2026-11-03T15:04:44.074402+00:00 | test_writer:src/test/java/com/dogGetDrunk/meetjyou/notice/NoticeBroadcastApiIntegrationTest.kt | gpt-6.1-terra | high | 이전 dispatch ctx_2e75f5bca707 failed | task_3b81cad1e2a0 | ctx_d033962efd9a |
| 2026-11-03T15:07:57.641796+00:00 | test_writer:src/test/java/com/dogGetDrunk/meetjyou/chat/ChatUnreadAggregationIntegrationTest.kt | gpt-6.1-terra | high | 이전 dispatch ctx_cfd3d08dcca6 failed | task_4ad94a8716b8 | ctx_010dc31ddcb7 |
| 2026-11-03T15:08:03.064515+00:00 | test_writer:src/test/java/com/dogGetDrunk/meetjyou/notification/BroadcastAlreadySentTest.kt | gpt-6.1-terra | high | 이전 dispatch ctx_ba66bf225243 failed | task_e275108440b7 | ctx_c37d84e957c8 |
| 2026-11-03T15:08:08.131191+00:00 | test_writer:src/test/java/com/dogGetDrunk/meetjyou/notification/BroadcastPreferenceBatchTest.kt | gpt-6.1-terra | high | 이전 dispatch ctx_8abc7a356dde failed | task_c3bbb37bb131 | ctx_57d43e466b01 |
| 2026-11-03T15:08:13.023045+00:00 | test_writer:src/test/java/com/dogGetDrunk/meetjyou/notification/TermsReconsentFailureIntegrationTest.kt | gpt-6.1-terra | high | 이전 dispatch ctx_e0e45478ac48 failed | task_4a3610c0969e | ctx_4c7502c96618 |
| 2026-11-03T15:08:17.994724+00:00 | test_writer:src/test/java/com/dogGetDrunk/meetjyou/post/PostDetailConnectionIntegrationTest.kt | gpt-6.1-terra | high | 이전 dispatch ctx_ac5241a27c4a failed | task_5a115607134b | ctx_50cdd6d1d79c |
| 2026-11-03T15:08:22.656059+00:00 | test_writer:src/test/java/com/dogGetDrunk/meetjyou/terms/TermsReconsentBroadcastApiIntegrationTest.kt | gpt-6.1-terra | high | 이전 dispatch ctx_60fb2024d4a1 failed | task_458503043573 | ctx_a6b9076599f3 |
| 2026-11-03T15:08:34.588884+00:00 | test_writer:src/test/java/com/dogGetDrunk/meetjyou/notification/NoticeBroadcastFailureIntegrationTest.kt | gpt-6.1-terra | high | 이전 dispatch ctx_3fad80f8d160 failed | task_1a3c70bfa076 | ctx_3aee157ce2a8 |
| 2026-11-03T15:31:00.584360+00:00 | test_writer:src/test/java/com/dogGetDrunk/meetjyou/notification/NoticeBroadcastFailureIntegrationTest.kt | gpt-6.1-terra | xhigh | 이전 dispatch ctx_3aee157ce2a8 failed | task_0486e845a0c9 | ctx_7fad500a6d9e |
| 2026-11-03T15:31:05.425942+00:00 | test_writer:src/test/java/com/dogGetDrunk/meetjyou/notification/TermsReconsentFailureIntegrationTest.kt | gpt-6.1-terra | xhigh | 이전 dispatch ctx_4c7502c96618 failed | task_a53f150c5622 | ctx_4e4a1c168e31 |
| 2026-11-03T15:31:10.670158+00:00 | test_writer:src/test/java/com/dogGetDrunk/meetjyou/post/PostDetailConnectionIntegrationTest.kt | gpt-6.1-terra | xhigh | 이전 dispatch ctx_50cdd6d1d79c failed | task_47059be22134 | ctx_5a5353aa3e3a |
| 2026-11-03T15:31:15.946184+00:00 | test_writer:src/test/java/com/dogGetDrunk/meetjyou/terms/TermsReconsentBroadcastApiIntegrationTest.kt | gpt-6.1-terra | xhigh | 이전 dispatch ctx_a6b9076599f3 failed | task_5579be00a7d6 | ctx_49755653e3b6 |
| 2026-11-03T19:45:55.404155+00:00 | test_writer:src/test/java/com/dogGetDrunk/meetjyou/notification/NoticeBroadcastFailureIntegrationTest.kt | gpt-6.1-terra | xhigh | 이전 dispatch ctx_7fad500a6d9e stopped | task_f33236127b9d | ctx_e14ae4f86df2 |
| 2026-11-03T19:46:00.265503+00:00 | test_writer:src/test/java/com/dogGetDrunk/meetjyou/notification/TermsReconsentFailureIntegrationTest.kt | gpt-6.1-terra | xhigh | 이전 dispatch ctx_4e4a1c168e31 stopped | task_4f1fc254b409 | ctx_f77af019f362 |
| 2026-11-03T19:46:05.200244+00:00 | test_writer:src/test/java/com/dogGetDrunk/meetjyou/post/PostDetailConnectionIntegrationTest.kt | gpt-6.1-terra | xhigh | 이전 dispatch ctx_5a5353aa3e3a stopped | task_333c4242906e | ctx_b4307d3190ee |
| 2026-11-03T19:46:10.171649+00:00 | test_writer:src/test/java/com/dogGetDrunk/meetjyou/terms/TermsReconsentBroadcastApiIntegrationTest.kt | gpt-6.1-terra | xhigh | 이전 dispatch ctx_49755653e3b6 stopped | task_a6a6365877b6 | ctx_99db2536aa8d |
| 2026-11-04T09:55:24.226408+00:00 | T-20261103-chat-notice-post-perf-01 | claude-opus-5-5 | medium | retry | task_b33e32df718f | ctx_50f464161b9f |
| 2026-11-04T09:56:34.442689+00:00 | T-20261103-chat-notice-post-perf-02 | claude-opus-5-5 | medium | retry | task_1110eb5b0359 | ctx_43593aac19e7 |
| 2026-11-04T09:59:32.882673+00:00 | T-20261103-chat-notice-post-perf-01 | claude-opus-5-5 | high | retry | task_b33e32df718f | ctx_25e1dcd6e25e |
| 2026-11-04T10:00:11.419477+00:00 | T-20261103-chat-notice-post-perf-02 | claude-opus-5-5 | high | retry | task_1110eb5b0359 | ctx_51237021a625 |
| 2026-11-04T10:04:24.004947+00:00 | T-20261103-chat-notice-post-perf-02 | claude-opus-5-5 | high | retry | task_1110eb5b0359 | ctx_9931da951b55 |
| 2026-11-04T10:05:18.428896+00:00 | T-20261103-chat-notice-post-perf-01 | claude-opus-5-5 | high | retry | task_b33e32df718f | ctx_627c3c8599b1 |
| 2026-11-04T10:07:40.267605+00:00 | T-20261103-chat-notice-post-perf-02 | claude-opus-5-5 | xhigh | retry | task_1110eb5b0359 | ctx_08b924ef020a |
| 2026-11-04T10:10:16.179605+00:00 | T-20261103-chat-notice-post-perf-01 | claude-opus-5-5 | xhigh | retry | task_b33e32df718f | ctx_7f2fb642557a |
| 2026-11-04T10:17:10.465875+00:00 | T-20261103-chat-notice-post-perf-02 | claude-opus-5-5 | xhigh | retry | task_1110eb5b0359 | ctx_1d8c12679f54 |
| 2026-11-04T10:17:41.969015+00:00 | T-20261103-chat-notice-post-perf-01 | claude-opus-5-5 | xhigh | retry | task_b33e32df718f | ctx_33456a89c8f5 |
| 2026-11-04T10:19:43.346384+00:00 | T-20261103-chat-notice-post-perf-01 | claude-opus-5-5 | max | retry | task_b33e32df718f | ctx_3a88584f5158 |
| 2026-11-04T10:22:39.358805+00:00 | T-20261103-chat-notice-post-perf-02 | claude-opus-5-5 | max | retry | task_1110eb5b0359 | ctx_3f55e3eac2a2 |
| 2026-11-04T10:41:43.745816+00:00 | T-20261103-chat-notice-post-perf-01 | claude-opus-5-5 | max | retry | task_b33e32df718f | ctx_6bb2214d28cd |
| 2026-11-04T10:41:48.421500+00:00 | T-20261103-chat-notice-post-perf-02 | claude-opus-5-5 | max | retry | task_1110eb5b0359 | ctx_0665ebf612a9 |
| 2026-11-04T10:41:53.123177+00:00 | T-20261103-chat-notice-post-perf-03 | claude-opus-5-5 | medium | retry | task_69aa0bafaedc | ctx_ff06aea8a5e6 |
| 2026-11-04T10:50:00.889109+00:00 | T-20261103-chat-notice-post-perf-03 | claude-opus-5-5 | high | retry | task_69aa0bafaedc | ctx_556c5d0c89a1 |
| 2026-11-04T10:52:06.555175+00:00 | T-20261103-chat-notice-post-perf-03 | claude-opus-5-5 | high | retry | task_69aa0bafaedc | ctx_141e5ea90bb4 |
| 2026-11-04T10:56:53.922625+00:00 | T-20261103-chat-notice-post-perf-03 | claude-opus-5-5 | xhigh | retry | task_69aa0bafaedc | ctx_bea069510b28 |
| 2026-11-04T11:13:01.990741+00:00 | T-20261103-chat-notice-post-perf-03 | claude-opus-5-5 | xhigh | retry | task_5ba37fe019d2 | ctx_1751d674570a |
| 2026-11-04T11:19:43.075802+00:00 | T-20261103-chat-notice-post-perf-03 | claude-opus-5-5 | max | retry | task_5ba37fe019d2 | ctx_9406e5e6ad3e |
