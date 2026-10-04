/*
 * Copyright (C) 2025-2026 Ryanistr
 *
 * Derived from Rianixia-ThermalCore (https://github.com/ryanistr/Rianixia-ThermalCore),
 * modified for MaxManager (modifications: Copyright (C) 2026 Nader Magdy).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
use std::sync::{ atomic::{ AtomicBool }, Arc };
use rianixia_thermalcore::monitor::ThermalMonitor;

fn main() {
    #[cfg(feature = "simulator")]
    {
        let args: Vec<String> = std::env::args().collect();
        if args.len() > 1 && args[1] == "--simulate" {
            if args.len() < 3 {
                eprintln!("Usage: {} --simulate <trace_file.json>", args[0]);
                std::process::exit(1);
            }
            match rianixia_thermalcore::simulator::ThermalSimulator::new(&args[2]) {
                Ok(mut sim) => {
                    sim.run_simulation();
                    return;
                }
                Err(e) => {
                    eprintln!("Failed to initialize simulator: {}", e);
                    std::process::exit(1);
                }
            }
        }
    }

    let term_flag = Arc::new(AtomicBool::new(false));
    if
        let Err(e) = signal_hook::flag::register(
            signal_hook::consts::SIGTERM,
            Arc::clone(&term_flag)
        )
    {
        eprintln!("Failed to register SIGTERM: {}", e);
    }
    if
        let Err(e) = signal_hook::flag::register(
            signal_hook::consts::SIGINT,
            Arc::clone(&term_flag)
        )
    {
        eprintln!("Failed to register SIGINT: {}", e);
    }

    let mut monitor = ThermalMonitor::new();

    if let Err(e) = monitor.run(term_flag) {
        monitor.logger.error(&format!("Fatal runtime error: {}", e));
        monitor.logger.info("Saving data on fatal error...");

        if let Err(save_e) = monitor.learning_data.save() {
            monitor.logger.error(
                &format!("Failed to save learning data on error exit: {}", save_e)
            );
        }
        std::process::exit(1);
    }

    monitor.logger.info("Rianixia Thermal Core shutting down.");

    // monitor.user_pattern_tracker.finalize_session();

    if let Err(save_e) = monitor.learning_data.save() {
        monitor.logger.error(&format!("Failed to save data on clean exit: {}", save_e));
    }
    monitor.logger.info("Save complete. Exiting.");
}
