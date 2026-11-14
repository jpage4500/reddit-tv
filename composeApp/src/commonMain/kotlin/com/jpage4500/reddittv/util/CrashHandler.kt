package com.jpage4500.reddittv.util

// arms the platform's uncaught-exception hook so CrashManager.recordCrash() runs
// before the process dies. safe to call once per process; subsequent calls re-arm
// the hook (cheap, idempotent in effect).
expect fun installCrashHandler()
