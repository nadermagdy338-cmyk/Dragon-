/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
use serde::{ Deserialize, Serialize };

// ============================================================================
// THERMAL STATE DEFINITIONS
// ============================================================================

#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub enum ThermalState {
    Normal,
    Rising,
    Controlled,
    Critical,
    Recovery,
}

#[derive(Debug, Clone, Copy, PartialEq, PartialOrd)]
pub enum MitigationTier {
    None,
    Gentle,
    Moderate,
    Emergency,
}
