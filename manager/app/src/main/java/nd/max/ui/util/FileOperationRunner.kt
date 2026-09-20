/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0.
 */
package nd.max.ui.util

/**
 * Executes an already guarded request and records its measured result.
 * UI code may choose a request, but it cannot bypass [FileOpGuard] because this
 * bridge deliberately accepts only the request object produced by the UI flow.
 */
fun executeFileOperation(request: FileOpRequest): FileOpOutcome {
    val start = android.os.SystemClock.elapsedRealtime()
    val outcome = when (request.operation) {
        FileOperation.Copy -> FileSystemEngine.copy(
            request.sources,
            request.destination.orEmpty(),
            request.renamed,
        )
        FileOperation.Move -> FileSystemEngine.move(
            request.sources,
            request.destination.orEmpty(),
            request.renamed,
        )
        FileOperation.Delete -> FileSystemEngine.delete(request.sources)
        FileOperation.Rename -> FileSystemEngine.rename(
            request.sources.firstOrNull().orEmpty(),
            request.newName.orEmpty(),
        )
        FileOperation.CreateDirectory -> FileSystemEngine.createDirectory(
            request.destination.orEmpty(),
            request.newName.orEmpty(),
        )
        FileOperation.CreateFile -> FileSystemEngine.createFile(
            request.destination.orEmpty(),
            request.newName.orEmpty(),
        )
        // الصلاحيات والمالك: يُنفَّذان ثم **يُقاس** الأثر بإعادة قراءة `stat` — والحكم
        // الثلاثي يخرج كما هو («نُفِّذ وتُحقّق» ≠ «نُفِّذ ولم يُتحقّق» ≠ «فشل»).
        FileOperation.ChangePermissions -> FilePermissionOps.applyChmod(
            request.sources,
            request.newName.orEmpty(),
        )
        FileOperation.ChangeOwner -> FilePermissionOps.applyChown(
            request.sources,
            request.newName.orEmpty().substringBefore(':'),
            request.newName.orEmpty().substringAfter(':', ""),
        )
        FileOperation.Compress -> FileSystemEngine.compress(
            request.sources,
            request.destination.orEmpty(),
        )
        FileOperation.Extract -> FileSystemEngine.extract(
            request.sources.firstOrNull().orEmpty(),
            request.destination.orEmpty(),
        )
        FileOperation.WriteText -> FileSystemEngine.writeText(
            request.sources.firstOrNull().orEmpty(),
            request.content.orEmpty(),
        )
    }
    EventLog.result(
        screen = "file_manager",
        action = request.operation.name,
        target = request.destination ?: request.sources.firstOrNull(),
        success = outcome.ok,
        durationMs = android.os.SystemClock.elapsedRealtime() - start,
    )
    return outcome
}
