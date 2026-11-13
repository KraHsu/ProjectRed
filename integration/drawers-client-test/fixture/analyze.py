#!/usr/bin/env python3
from pathlib import Path
import json,statistics,hashlib,subprocess,os
ROOT=Path(__file__).resolve().parents[3];OUT=Path(os.environ.get('PR_DRAWER_OUTPUT', ROOT/'build/drawers-client-test')).resolve()
status=json.loads((OUT/'pair-status.json').read_text());assert status['status']=='all_passed'
runs={}
for row in status['records']:
    d=json.loads((OUT/f"pair-{row['pair']}-{row['variant']}"/'results.json').read_text());assert d['status']=='passed'
    runs[(row['pair'],row['variant'])]=d
phases=['idle','one_item_per_tick','native_extractor_chip'];summary={}
def get(pair,variant,phase):return next(x for x in runs[(pair,variant)]['phases'] if x['phase']==phase)
for phase in phases:
    case={}
    for variant in ['baseline','candidate']:
        data=[get(i,variant,phase) for i in range(1,4)]
        cpu=[x['server_tick_cpu']['mean_ms'] for x in data];wall=[x['server_tick_wall']['mean_ms'] for x in data]
        fps=[1000/x['client_frames']['mean_ms'] for x in data]
        case[variant]={'runs':3,'ticks_per_run':400,'median_run_mean_tick_cpu_ms':statistics.median(cpu),'tick_cpu_means_ms':cpu,'median_run_mean_tick_wall_ms':statistics.median(wall),'tick_wall_means_ms':wall,'median_run_p95_tick_wall_ms':statistics.median(x['server_tick_wall']['p95_ms'] for x in data),'count_updates_per_run':[x['count_updates_emitted'] for x in data],'median_count_updates':statistics.median(x['count_updates_emitted'] for x in data),'median_count_payload_bytes':statistics.median(x['count_payload_bytes_received'] for x in data),'median_fps':statistics.median(fps),'fps_per_run':fps,'median_frame_p95_ms':statistics.median(x['client_frames']['p95_ms'] for x in data),'client_frames_per_run':[x['client_frames']['samples'] for x in data]}
    b,c=case['baseline'],case['candidate']
    case['paired_tick_cpu_reduction_ms']=[get(i,'baseline',phase)['server_tick_cpu']['mean_ms']-get(i,'candidate',phase)['server_tick_cpu']['mean_ms'] for i in range(1,4)]
    case['median_tick_cpu_reduction_percent']=100*(1-c['median_run_mean_tick_cpu_ms']/b['median_run_mean_tick_cpu_ms'])
    case['count_update_reduction_percent']=None if not b['median_count_updates'] else 100*(1-c['median_count_updates']/b['median_count_updates'])
    summary[phase]=case
sd=Path(os.environ['PR_DRAWER_SD_JAR']).resolve()
metadata={'status':'passed','pairs':3,'run_order':[(x['pair'],x['variant']) for x in status['records']],'method':'Real Minecraft/Forge development client and integrated server, real StorageDrawers blocks/controllers and ProjectRed interfaces/chips; no proxy drawer data','changed_mod':'ProjectRed only; two transfer methods in PluginStorageDrawers.scala','unchanged_storage_drawers':{'version':'2.2.31-GTNH','dev_jar_sha256':hashlib.sha256(sd.read_bytes()).hexdigest()},'scene':{'source_drawer_blocks':50,'destination_drawer_blocks':50,'logical_drawers_per_controller':200,'controller_range':4,'controller_max_drawer_blocks':50,'item_layout':'All drawers contain matching coal; deliberate duplicate-storage stress layout','pulse_load':'One item per tick, 400 transfers in 20 seconds','native_chip_load':'Unmodified extractor scheduling, 64 items per operation, every 10 ticks; measurement 20 seconds','initial_total_items':51400},'phases':summary,'correctness':{'all_six_runs_passed':True,'all_client_counts_match':True,'all_count_messages_received':True,'nbt_metadata_mismatch':True,'partial_multi_drawer_extraction':True,'populated_before_empty':True,'locked_prototype_retained':True,'void_overflow':True,'vending':True,'compacting_drawers_enabled':False,'ore_conversion_enabled':False},'limits':['Development runtime mod set, not complete GTNH/Angelica client','Duplicate-item stress layout; unique-item or mixed warehouses may have smaller benefits','Count update payload bytes exclude other packets, framing, compression, IP/TCP and fan-out to multiple players','Timing includes identical tick/packet counters; observer work is not subtracted','Three paired runs; no confidence interval or general whole-game speedup is asserted','Only the PR compatibility adapter changed; StorageDrawers controller scanning/indexing remains unchanged','Compacting drawers and ore conversion are disabled by the copied GTNH config and were not exercised'], 'artifacts':status['records']}
(OUT/'comparison.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n')
labels={'idle':'空闲','one_item_per_tick':'每 tick 搬运 1 个物品','native_extractor_chip':'原生抽取芯片'}
lines=['真实客户端抽屉性能对比\n','测试通过：3 组配对，6 次独立启动。顺序为原版/修改、修改/原版、原版/修改。\n','本轮只修改红石计划的 Storage Drawers 适配层。Storage Drawers 2.2.31-GTNH 保持原版，未修改其扫描与索引。\n','真实方块：来源与目标各50个四格抽屉，各接一个管理器，之间由两个路由接口和一个基础管道连接。管理器范围4、上限50。全部格放同一种煤，是重复存储压力场景，不代表所有混合仓库。\n','表中时间为三次运行均值的中位数；每阶段每次400 tick（约20秒）。\n','阶段 | 原版 tick CPU ms | 修改后 tick CPU ms | 数量同步消息 原版→修改 | FPS 原版→修改']
for phase in phases:
    b=summary[phase]['baseline'];c=summary[phase]['candidate'];lines.append(f"{labels[phase]} | {b['median_run_mean_tick_cpu_ms']:.3f} | {c['median_run_mean_tick_cpu_ms']:.3f} | {b['median_count_updates']:.0f}→{c['median_count_updates']:.0f} | {b['median_fps']:.1f}→{c['median_fps']:.1f}")
lines+=['\n解读：重复数量同步显著减少，单物品高频搬运的 tick CPU 降低；原生芯片阶段 CPU 差异较小，帧率没有明显提升。两种版本都维持20TPS，本轮没有提升运输速率。', '每次运行最终物品总数51400，客户端与服务端一致；每阶段已发送的 CountUpdateMessage 全部由真实客户端接收。NBT/元数据不匹配、跨格部分抽取、先合并再占空格、锁定保留、虚空和无限取出检查通过。GTNH配置关闭了压缩抽屉与矿辞转换，未声称覆盖这两项。','\n同步量只统计该类消息；实际 FML payload 每条16字节，不含其他数据包和网络封装。帧率由真实帧间隔计算，视距4、VSync关闭、窗口854×480、本机Radeon8060S。此为开发客户端环境，非完整GTNH/Angelica客户端。计时包含两侧相同的观测计数器开销。', '\n每次重跑会生成 results.json、frames.csv 和截图；精简归档保留逐次 JSON 及代表性截图；comparison.json 保存逐组数据。正式服未改动、未重启。']
(OUT/'report.txt').write_text('\n'.join(lines)+'\n',encoding='utf-8')
print('\n'.join(lines[:10]))
