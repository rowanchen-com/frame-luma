"""Encode our original colour fields as a silent hardware-decodable loop. Requires FFmpeg."""
import argparse
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

HERE = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ffmpeg', default='ffmpeg')
    parser.add_argument('--variants', nargs='+', help='Video names from BackdropVideo; default: all')
    args = parser.parse_args()
    source = (HERE.parent / 'src/ui/ColorField.kt').read_text(encoding='utf-8-sig')

    def constant(name):
        return int(re.search(r'const val ' + name + r'\s*=\s*(\d+)', source).group(1))

    count, seconds = constant('PHASE_COUNT'), constant('TRANSITION_MS') / 1000
    frames = [HERE / f'frame-{i}.png' for i in range(count)]
    if not all(frame.is_file() for frame in frames):
        raise SystemExit('Missing original background keyframes')
    poster = HERE.parent / 'src/main/res/drawable-nodpi/backdrop_poster.png'
    poster.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(frames[0], poster)
    policy = (HERE.parent / 'src/ui/UiPerformance.kt').read_text(encoding='utf-8-sig')
    variants = re.findall(r'^\s*([A-Z]+)\(R\.raw\.([a-z_]+),\s*(\d+),\s*(\d+),\s*(\d+)\)', policy, re.M)
    if not variants:
        raise SystemExit('Missing BackdropVideo definitions')
    selected = {name.lower() for name, *_ in variants} if not args.variants else set(args.variants)
    if selected - {name.lower() for name, *_ in variants}:
        raise SystemExit('Unknown video variant')
    output_dir = HERE.parent / 'src/main/res/raw'
    output_dir.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as temp:
        listing = Path(temp) / 'frames.txt'
        lines = []
        for frame in frames:
            lines += [f"file '{frame.as_posix()}'", f'duration {seconds}']
        lines += [f"file '{frames[0].as_posix()}'"]
        listing.write_text('\n'.join(lines) + '\n', encoding='utf-8')
        for name, resource, width, height, fps in variants:
            if name.lower() not in selected:
                continue
            output = output_dir / (resource + '.mp4')
            subprocess.run([
                args.ffmpeg, '-hide_banner', '-loglevel', 'error', '-y',
                '-f', 'concat', '-safe', '0', '-i', str(listing),
                '-vf', f'format=yuv444p,framerate=fps={fps}:interp_start=0:interp_end=255:scene=100,scale={width}:{height}:flags=bilinear,format=yuv420p',
                '-t', str(count * seconds), '-an', '-c:v', 'libx265', '-preset', 'medium',
                '-crf', '16', '-tag:v', 'hvc1', '-x265-params', f'log-level=error:pools=4:keyint={fps}:min-keyint={fps}:scenecut=0',
                '-movflags', '+faststart', '-map_metadata', '-1', str(output),
            ], check=True)
            print(f'{output.name}: {output.stat().st_size} bytes, {count * seconds:g}s, {width}x{height}@{fps}, HEVC, no audio')


if __name__ == '__main__':
    main()
