# Experimental session

This note is not a compatibility list and not a final write-up. It records
one phone. Do not treat TimerRoot as a universal root method for other
models until that model has been tested.

Nothing here asks the original project to keep a device matrix.

## Device

| | |
| --- | --- |
| Phone | OnePlus 15 (CPH2747) |
| System | ColorOS 17 / Android 17 |
| Kernel | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k` |

That kernel string matches the build the upstream exploit binary names. Matching
the string is not, by itself, proof that another phone will work.

## What this fork changes

- The Gradle wrapper is included, and AIDL generation is on, so the Shizuku
  service interface builds with Android Gradle Plugin 8.
- Prepare copies `assets/timer` into `/data/local/tmp/timer` and `assets/ksud`
  into `/data/local/tmp/ksud` as the shell user (`chmod 755`). The exploit
  already looks at `/data/local/tmp/ksud` before it prints `ksud missing`.
- Get root shell starts the KernelSU stage only when the log shows
  `uid=0(root)`, or `setuid(0)=0` together with `resuid=0/0/`. The line
  `root shell started` is printed even when calibration fails.

## Confirmed result

On this phone, after a reboot, one Prepare and one Get root shell, the log
showed:

- `page_alloc=334` `page_free=332`, base `count=481`
- shell stage: `setuid(0)=0` and `resuid=0/0/2000`
- KernelSU stage: `ksud=/data/local/tmp/ksud version=ksud 3.3.1 (uapi: 5)`,
  `late-load rc=0`, `KSU_LOADED`
- the root session worked afterwards

The closing line `fake cred page held; reboot to clean -- parking without reap
(zombie held)` is the exploit's normal end after a successful late-load. The
parent stays alive and does not reap the zombie. A full reboot removes that
root.

`sh: can't find tty fd` only means the shell had no terminal.

The same log block can appear twice. The app reads the log while the run is
in progress and again when it finishes. Same pid means one run.

## Limitations

- Root is temporary. It lasts until a full reboot, and only while the parked
  process is left alone. Killing it can panic the kernel. That warning is the
  exploit author's, and it matched this session.
- Calibration can fail with `best=0 count=0`. On an earlier boot of this same
  phone the page ids came out swapped (`page_alloc=332` `page_free=334`) and
  the run stopped before any credential write. The working boot used `334` /
  `332`. Finding those ids is inside the `timer` binary, which this repository
  does not contain. A failed calibration is not a TimerRoot packaging bug.
- The first plant can miss (`burst missed; holding zombie`) and a later attempt
  can still succeed. The missed attempt still holds a zombie.
- `ksud 3.3.1 (uapi: 5)` is what ran here. The exploit notes also mention a
  later `uapi: 6` build. This session does not show that those builds are
  interchangeable on every kernel.

## Binaries

This fork does not publish `timer` or `ksud`.

- `app/src/main/assets/timer` stays gitignored. The binary belongs to the
  CVE-2026-98260 project. Check that license before distributing a copy.
- `app/src/main/assets/ksud` stays gitignored. KernelSU is GPL-2.0. Check that
  license before distributing `ksud` or `libksud.so`.
- `gradle/wrapper/gradle-wrapper.jar` is the Gradle wrapper used to build the
  app. It is not an exploit binary.

The app code remains under the original MIT license. Copyright for TimerRoot
stays with kpgc10kai.

## Credits

- App: [kpgc10kai/TimerRoot](https://github.com/kpgc10kai/TimerRoot)
- Exploit: [YuKongA/CVE-2026-98260](https://github.com/YuKongA/CVE-2026-98260)
- KernelSU: [tiann/KernelSU](https://github.com/tiann/KernelSU)
