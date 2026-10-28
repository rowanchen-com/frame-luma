"""Read-only check of FrameLuma's actual main window; never changes wm size/density."""
import argparse
import json
import re
import subprocess
from pathlib import Path

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial',required=True)
parser.add_argument('--adb',default='adb')
parser.add_argument('--output',type=Path,default=Path('.local/ui-check'))
args=parser.parse_args()
base=[args.adb,'-s',args.serial]
def run(*command):
    return subprocess.run(base+list(command),check=True,capture_output=True,timeout=15).stdout
dump=run('shell','dumpsys','activity','top').decode('utf-8',errors='replace')
match=re.search(r'(?m)^  ACTIVITY com\.xiaomi\.mitv\.light/\.MainActivity\b',dump)
assert match,'FrameLuma main activity is not present'
end=dump.find('\n  ACTIVITY ',match.end())
block=dump[match.start():end if end>=0 else len(dump)]
assert 'mResumed=true' in block,'FrameLuma is covered or paused; dismiss screensaver first'
start=block.find('    View Hierarchy:')
hierarchy=block[start:block.find('    Looper ',start)]
assert '[MainActivity]' in hierarchy,'Open the FrameLuma main window first'
args.output.mkdir(parents=True,exist_ok=True)
(args.output/'hierarchy.txt').write_text(hierarchy,encoding='utf-8')
(args.output/'screen.png').write_bytes(run('exec-out','screencap','-p'))
assert 'android.widget.ScrollView{' not in hierarchy,'Main screen still scrolls/clips on the actual logical window'
for name in ['dashboard_panel','brand_title','light_toggle','brightness_control','speed_control','direction_control','calibration_control','about_control']:
    assert 'id/'+name in hierarchy,f'Missing main control: {name}'
report={'main_window':True,'scroll_view':False,'all_main_controls_present':True}
window=re.search(r'android\.widget\.FrameLayout\{[^\n]*? 0,0-(\d+),(\d+)',hierarchy)
panel=re.search(r'DashboardPanel\{[^\n]*? (\d+),(\d+)-(\d+),(\d+) [^\n]*id/dashboard_panel',hierarchy)
assert window and panel,'Window geometry missing'
w,h=map(int,window.groups());left,top,right,bottom=map(int,panel.groups())
assert abs((left+right)/2-w/2)<=1 and abs((top+bottom)/2-h/2)<=1,'Dashboard is not centered'
for name in ['brand_title','light_toggle','brightness_control','speed_control','direction_control','calibration_control','about_control']:
    rect=re.search(r'\{[^\n]*? (\d+),(\d+)-(\d+),(\d+) [^\n]*id/'+name,hierarchy)
    assert rect,f'Bounds missing: {name}'
    x,y,x2,y2=map(int,rect.groups())
    assert 0<=x<x2<=right-left and 0<=y<y2<=bottom-top,f'Control clipped: {name}'
report.update({'window_px':[w,h],'centered':True,'dashboard_bounds':[left,top,right,bottom],'all_controls_inside_panel':True})
(args.output/'result.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report))
