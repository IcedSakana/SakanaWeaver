"""
SakanaWeaver AI Engine — Harmony (Chord Progression) Generation Module

Generates chord progressions conditioned on:
- Melody contour
- Key / mode
- Style label
"""

import torch
import torch.nn as nn
from dataclasses import dataclass


@dataclass
class HarmonyConfig:
    num_chord_classes: int = 48   # 12 roots × 4 qualities (maj, min, dim, dom7)
    melody_vocab: int = 128
    max_seq_len: int = 256
    d_model: int = 256
    n_heads: int = 4
    n_layers: int = 4
    dropout: float = 0.1


class HarmonyModel(nn.Module):
    """Seq2Seq model: melody → chord progression."""

    def __init__(self, config: HarmonyConfig):
        super().__init__()
        self.config = config

        self.melody_encoder = nn.Embedding(config.melody_vocab, config.d_model)
        self.pos_enc = nn.Embedding(config.max_seq_len, config.d_model)

        encoder_layer = nn.TransformerEncoderLayer(
            d_model=config.d_model,
            nhead=config.n_heads,
            dropout=config.dropout,
            batch_first=True,
        )
        self.encoder = nn.TransformerEncoder(encoder_layer, num_layers=config.n_layers)
        self.chord_head = nn.Linear(config.d_model, config.num_chord_classes)

    def forward(self, melody: torch.Tensor) -> torch.Tensor:
        seq_len = melody.size(1)
        positions = torch.arange(seq_len, device=melody.device).unsqueeze(0)

        h = self.melody_encoder(melody) + self.pos_enc(positions)
        h = self.encoder(h)
        return self.chord_head(h)
