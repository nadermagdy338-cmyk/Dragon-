/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
use std::fs;
use std::thread;
use std::time::Duration;
use std::process::Command;
use std::os::unix::fs::PermissionsExt;
use std::path::Path;
use glob::glob;

pub mod logger;
pub mod plan;
use crate::utils::logger::{log_info, verbose, log_warn, log_error};
use crate::utils::plan::{apply_prop, Effect, PropTool};

pub const MY_PATH: &str = "/system/bin:/system/xbin:/data/adb/ap/bin:/data/adb/ksu/bin:/data/adb/magisk:/debug_ramdisk:/sbin:/sbin/su:/su/bin:/su/xbin:/data/data/com.termux/files/usr/bin";

// `persist.sys.maxmanager*` property keys this crate touches. Kept together
// here (mirroring `binprofiles/src/props.rs`) instead of inline, so a rename
// only needs one edit. The manager app (`MaxManagerProps.kt`) and the shell
// scripts (`mainfiles/common/props.sh`) use these exact same key strings.
pub const PROP_DEBUG_MODE: &str = "persist.sys.maxmanager.debugmode";
pub const PROP_STATE: &str = "persist.sys.maxmanager.state";
pub const PROP_CONF_FSTRIM: &str = "persist.sys.maxmanagerconf.fstrim";

pub fn getprop(key: &str) -> String {
    match Command::new("getprop").arg(key).output() {
        Ok(output) => String::from_utf8_lossy(&output.stdout).trim().to_string(),
        Err(e) => {
            log_error(&format!("Failed to getprop '{}': {}", key, e));
            String::new()
        }
    }
}

pub fn resetprop(key: &str, val: &str) {
    let (program, args) = plan::prop_invocation(PropTool::ResetProp, key, val);
    if let Err(e) = Command::new(program).args(args).status() {
        log_error(&format!("Failed to resetprop '{}': {}", key, e));
    }
}

pub fn setprop(key: &str, val: &str) {
    let (program, args) = plan::prop_invocation(PropTool::SetProp, key, val);
    if let Err(e) = Command::new(program).args(args).status() {
        log_error(&format!("Failed to setprop '{}' to '{}': {}", key, val, e));
    }
}

pub fn get_debugmode() -> bool {
    getprop(PROP_DEBUG_MODE) == "true"
}

pub fn get_fstrim_state() -> String {
    getprop(PROP_CONF_FSTRIM)
}

pub fn chmod(path: &str, mode: u32) {
    match fs::metadata(path) {
        Ok(metadata) => {
            let mut perms = metadata.permissions();
            perms.set_mode(mode);
            if let Err(e) = fs::set_permissions(path, perms) {
                log_error(&format!("Failed to set permissions for {}: {}", path, e));
            }
        },
        Err(e) => log_warn(&format!("Failed to read metadata (chmod) for {}: {}", path, e)),
    }
}

pub fn systemv(command: &str) -> i32 {
    match Command::new("/system/bin/sh")
        .arg("-c")
        .arg(command)
        .env("PATH", MY_PATH)
        .status()
    {
        Ok(status) => status.code().unwrap_or(-1),
        Err(e) => {
            log_error(&format!("systemv failed for '{}': {}", command, e));
            -1
        }
    }
}

pub fn execute_command(command: &str) -> Option<String> {
    match Command::new("/system/bin/sh")
        .arg("-c")
        .arg(command)
        .env("PATH", MY_PATH)
        .output()
    {
        Ok(output) if output.status.success() => {
            Some(String::from_utf8_lossy(&output.stdout).trim().to_string())
        },
        Ok(output) => {
            log_warn(&format!("Command '{}' failed with status: {}", command, output.status));
            None
        },
        Err(e) => {
            log_error(&format!("execute_command failed for '{}': {}", command, e));
            None
        }
    }
}

pub fn setsgov(gov: &str) {
    match glob(plan::CPU_GOVERNOR_GLOB) {
        Ok(paths) => {
            let mut applied = false;
            for path in paths.flatten() {
                if let Some(p_str) = path.to_str() {
                    chmod(p_str, plan::SYSFS_WRITE_MODE);
                    if let Err(e) = fs::write(p_str, gov) {
                        log_error(&format!("Failed to write CPU governor to {}: {}", p_str, e));
                    } else {
                        applied = true;
                    }
                    chmod(p_str, plan::SYSFS_RESTORE_MODE);
                }
            }
            if applied {
                log_info(&format!("Set current CPU Governor to {}", gov));
            } else {
                log_warn(&format!("Failed to set CPU Governor to {}. No paths updated.", gov));
            }
        },
        Err(e) => log_error(&format!("Glob pattern failed for CPU scaling_governor: {}", e)),
    }
}

pub fn sets_io(scheduler: &str) {
    let mut applied = false;
    for block in &plan::IO_BLOCK_DEVICES {
        let path = plan::io_scheduler_path(block);
        if Path::new(&path).exists() {
            chmod(&path, plan::SYSFS_WRITE_MODE);
            if let Err(e) = fs::write(&path, scheduler) {
                log_error(&format!("Failed to write IO scheduler to {}: {}", path, e));
            } else {
                applied = true;
            }
            chmod(&path, plan::SYSFS_RESTORE_MODE);
        }
    }
    if applied {
        log_info(&format!("Set current IO Scheduler to {}", scheduler));
    } else {
        log_warn(&format!("IO Scheduler path not found or failed for {}", scheduler));
    }
}

pub fn sets_mali_gov(gov: &str) {
    match glob(plan::MALI_GOVERNOR_GLOB) {
        Ok(paths) => {
            let mut applied = false;
            for path in paths.flatten() {
                if let Some(p_str) = path.to_str() {
                    chmod(p_str, plan::SYSFS_WRITE_MODE);
                    if let Err(e) = fs::write(p_str, gov) {
                        log_error(&format!("Failed to write Mali Governor to {}: {}", p_str, e));
                    } else {
                        applied = true;
                    }
                    chmod(p_str, plan::SYSFS_RESTORE_MODE); 
                }
            }
            if applied {
                log_info(&format!("Set current Mali GPU Governor to {}", gov));
            } else {
                log_warn("No Mali GPU governor paths found or updated.");
            }
        },
        Err(e) => log_error(&format!("Glob pattern failed for Mali governor: {}", e)),
    }
}

pub fn setthermalcore(state: &str) {
    if plan::thermalcore_starts(state) {
        if systemv(plan::THERMALCORE_SPAWN) != 0 {
            log_error("Failed to spawn Thermalcore service");
            return;
        }
        thread::sleep(Duration::from_secs(plan::THERMALCORE_SETTLE_SECS));

        if let Some(pid) = execute_command(plan::THERMALCORE_PROBE) {
            if !pid.is_empty() {
                log_info(&format!("Starting Thermalcore Service with pid {}", pid));
            } else {
                log_warn("Thermalcore service started but PID not found");
            }
        } else {
            log_error("Failed to execute pgrep for Thermalcore");
        }
    } else {
        if systemv(plan::THERMALCORE_KILL) != 0 {
            log_error("Failed to stop Thermalcore service");
        } else {
            log_info("Stopped Thermalcore service");
        }
    }
}

pub fn fstrim() {
    if get_fstrim_state() == "1" {
        verbose("Triggering Android native fstrim...");
        
        let attempts = plan::fstrim_attempts();
        let mut status = systemv(attempts[0]);
        
        if status != 0 {
            verbose("fstrim failed or not found, retry...");
            status = systemv(attempts[1]);
        }

        if status == 0 {
            log_info("Trimmed unused blocks successfully via Android native framework");
        } else {
            log_error(&format!("All native fstrim commands failed to execute. Status: {}", status));
        }
    }
}

pub fn enable_dnd() {
    if systemv(plan::dnd_command(true)) == 0 {
        log_info("DND enabled");
    } else {
        log_error("Failed to enable DND");
    }
}

pub fn disable_dnd() {
    if systemv(plan::dnd_command(false)) == 0 {
        log_info("DND disabled");
    } else {
        log_error("Failed to disable DND");
    }
}

pub fn setrefreshrates(rate: &str) {
    let target_fps = rate.parse::<i32>().unwrap_or(60);

    let status = systemv(&plan::refresh_rate_command(rate));

    if status == 0 {
        log_info(&format!("Triggered receiver app to apply {}Hz", target_fps));
    } else {
        log_warn("Triggered receiver app, but command did not report success");
    }
}

pub fn restartservice() {
    let _ = systemv(plan::THERMALCORE_KILL);
    let _ = systemv(plan::SERVICE_KILL);
    let _ = systemv(plan::APPMONITORING_KILL);
    
    setprop(PROP_STATE, "stopped");
    
    if systemv(plan::RESTART_SCRIPT) != 0 {
        log_error("Failed to restart service script");
    } else {
        log_info("Restarted MaxManager services");
    }
}

pub fn setrender(renderer: &str) {
    // الأثر مرتّب في `plan::setrender_effects` (سطح القياس)، وهنا التنفيذ وحده —
    // فلا نسخة ثانية من المنطق تنحرف عن الجدول المرجعي بلا أن يسقط اختبار.
    for effect in plan::setrender_effects(renderer) {
        if let Effect::Prop(write) = effect {
            apply_prop(&write);
        }
    }

    if renderer == "default" || renderer.is_empty() {
        log_info("Resetting all renderers to system default");
    } else {
        log_info(&format!("Successfully applied renderer: {}", renderer));
    }
}

pub fn check_mali_path() {
    let mut found = false;
    if let Ok(paths) = glob::glob(plan::MALI_DIR_GLOB) {
        for _path in paths.flatten() {
            found = true;
            break;
        }
    }

    let (text, code) = plan::mali_path_result(found);
    println!("{}", text);
    std::process::exit(code);
}
