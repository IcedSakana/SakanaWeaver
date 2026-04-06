"""
SakanaWeaver AI Engine — Style Embedding Module

Converts discrete style profiles into continuous vector representations
that condition all generation models.
"""

import torch
import torch.nn as nn
from dataclasses import dataclass


@dataclass
class StyleEmbeddingConfig:
    num_styles: int = 64          # max registered styles
    style_dim: int = 128          # style embedding dimension
    num_continuous_features: int = 20  # number of continuous style features


# Ordered list of continuous features extracted from a StyleProfile
CONTINUOUS_FEATURE_KEYS = [
    'bpm_normalized',             # (bpm - 60) / 140
    'swing',
    'syncopation_level',
    'chord_complexity',
    'modulation_frequency',
    'chromaticism_level',
    'melody_pitch_range',         # normalized
    'melody_max_leap',            # normalized
    'melody_step_to_leap_ratio',
    'melody_note_density',        # normalized
    'melody_rest_frequency',
    'melody_repetition_level',
    'melody_scale_adherence',
    'arrangement_dynamic_range',
    'mixing_reverb_level',
    'mixing_stereo_width',
    'mixing_compression',
    'mixing_brightness_eq',
    'mixing_low_end_emphasis',
    'energy_mean',                # mean energy across sections
]


def extract_continuous_features(style_dict: dict) -> list[float]:
    """Extract normalized continuous features from a style profile dict."""
    rhythm = style_dict.get('rhythm', {})
    harmony = style_dict.get('harmony', {})
    melody = style_dict.get('melody', {})
    arrangement = style_dict.get('arrangement', {})
    mixing = style_dict.get('mixing', {})

    bpm_range = rhythm.get('bpmRange', [100, 130])
    default_bpm = rhythm.get('defaultBpm', 120)

    energy_curve = arrangement.get('energyCurve', {})
    energy_values = list(energy_curve.values()) if energy_curve else [0.5]

    pitch_range = melody.get('pitchRange', [60, 84])
    pitch_span = (pitch_range[1] - pitch_range[0]) / 48.0  # normalize to ~1.0

    return [
        (default_bpm - 60) / 140.0,
        rhythm.get('swing', 0.0),
        rhythm.get('syncopationLevel', 0.3),
        harmony.get('chordComplexity', 0.3),
        harmony.get('modulationFrequency', 0.1),
        harmony.get('chromaticismLevel', 0.1),
        pitch_span,
        melody.get('maxLeap', 12) / 24.0,
        melody.get('stepToLeapRatio', 0.6),
        melody.get('noteDensity', 2.0) / 5.0,
        melody.get('restFrequency', 0.2),
        melody.get('repetitionLevel', 0.5),
        melody.get('scaleAdherence', 0.8),
        arrangement.get('dynamicRange', 0.5),
        mixing.get('reverbLevel', 0.3),
        mixing.get('stereoWidth', 0.5),
        mixing.get('compressionAmount', 0.5),
        mixing.get('brightnessEQ', 0.0),
        mixing.get('lowEndEmphasis', 0.5),
        sum(energy_values) / len(energy_values),
    ]


class StyleEncoder(nn.Module):
    """
    Encodes a style profile into a dense vector.

    Two pathways:
    1. Learned embedding lookup for known style IDs
    2. MLP encoder for continuous features (supports novel/blended styles)

    Outputs are fused into a single style vector.
    """

    def __init__(self, config: StyleEmbeddingConfig):
        super().__init__()
        self.config = config

        # Pathway 1: discrete style ID embedding
        self.style_lookup = nn.Embedding(config.num_styles, config.style_dim)

        # Pathway 2: continuous feature encoder
        self.feature_encoder = nn.Sequential(
            nn.Linear(config.num_continuous_features, config.style_dim),
            nn.GELU(),
            nn.Linear(config.style_dim, config.style_dim),
            nn.LayerNorm(config.style_dim),
        )

        # Fusion gate: learns how much to rely on discrete vs continuous
        self.gate = nn.Sequential(
            nn.Linear(config.style_dim * 2, 1),
            nn.Sigmoid(),
        )

    def forward(
        self,
        style_id: torch.Tensor | None = None,
        continuous_features: torch.Tensor | None = None,
    ) -> torch.Tensor:
        """
        Args:
            style_id: (batch,) integer tensor of style IDs, or None for novel styles
            continuous_features: (batch, num_continuous_features) float tensor

        Returns:
            (batch, style_dim) style embedding vector
        """
        if style_id is not None and continuous_features is not None:
            emb_discrete = self.style_lookup(style_id)
            emb_continuous = self.feature_encoder(continuous_features)
            gate_input = torch.cat([emb_discrete, emb_continuous], dim=-1)
            alpha = self.gate(gate_input)
            return alpha * emb_discrete + (1 - alpha) * emb_continuous

        if style_id is not None:
            return self.style_lookup(style_id)

        if continuous_features is not None:
            return self.feature_encoder(continuous_features)

        raise ValueError("At least one of style_id or continuous_features must be provided")


class StyleConditioner(nn.Module):
    """
    Injects style information into a sequence model via FiLM conditioning.
    (Feature-wise Linear Modulation)

    Given style embedding s, produces scale γ and shift β applied to hidden states:
        h' = γ(s) * h + β(s)
    """

    def __init__(self, style_dim: int, hidden_dim: int):
        super().__init__()
        self.scale = nn.Linear(style_dim, hidden_dim)
        self.shift = nn.Linear(style_dim, hidden_dim)

    def forward(self, hidden: torch.Tensor, style_emb: torch.Tensor) -> torch.Tensor:
        """
        Args:
            hidden: (batch, seq_len, hidden_dim)
            style_emb: (batch, style_dim)
        """
        gamma = self.scale(style_emb).unsqueeze(1)  # (batch, 1, hidden_dim)
        beta = self.shift(style_emb).unsqueeze(1)
        return gamma * hidden + beta
