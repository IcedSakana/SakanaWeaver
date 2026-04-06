"""
SakanaWeaver AI Engine — Harmony (Chord Progression) Generation Module

Generates chord progressions conditioned on:
- Melody contour
- Key / mode
- Style embedding (determines chord complexity, voicing, quality preferences)
"""

import torch
import torch.nn as nn
from dataclasses import dataclass

from ..style_embedding import StyleEncoder, StyleConditioner, StyleEmbeddingConfig


@dataclass
class HarmonyConfig:
    num_chord_classes: int = 48   # 12 roots × 4 qualities (maj, min, dim, dom7)
    melody_vocab: int = 128
    max_seq_len: int = 256
    d_model: int = 256
    n_heads: int = 4
    n_layers: int = 4
    dropout: float = 0.1
    style_dim: int = 128


class HarmonyModel(nn.Module):
    """Seq2Seq model: (melody + style) → chord progression."""

    def __init__(self, config: HarmonyConfig):
        super().__init__()
        self.config = config

        self.melody_encoder = nn.Embedding(config.melody_vocab, config.d_model)
        self.pos_enc = nn.Embedding(config.max_seq_len, config.d_model)

        # Style
        self.style_encoder = StyleEncoder(StyleEmbeddingConfig(style_dim=config.style_dim))
        self.style_proj = nn.Linear(config.style_dim, config.d_model)
        self.style_conditioners = nn.ModuleList([
            StyleConditioner(config.style_dim, config.d_model)
            for _ in range(config.n_layers)
        ])

        encoder_layer = nn.TransformerEncoderLayer(
            d_model=config.d_model,
            nhead=config.n_heads,
            dropout=config.dropout,
            batch_first=True,
        )
        self.encoder_layers = nn.ModuleList([
            nn.TransformerEncoderLayer(
                d_model=config.d_model,
                nhead=config.n_heads,
                dropout=config.dropout,
                batch_first=True,
            )
            for _ in range(config.n_layers)
        ])
        self.chord_head = nn.Linear(config.d_model, config.num_chord_classes)

    def forward(
        self,
        melody: torch.Tensor,
        style_id: torch.Tensor | None = None,
        style_features: torch.Tensor | None = None,
    ) -> torch.Tensor:
        seq_len = melody.size(1)
        positions = torch.arange(seq_len, device=melody.device).unsqueeze(0)

        h = self.melody_encoder(melody) + self.pos_enc(positions)

        # Add style as a prefix token (cross-attention style)
        style_emb = self.style_encoder(style_id=style_id, continuous_features=style_features)
        style_token = self.style_proj(style_emb).unsqueeze(1)  # (batch, 1, d_model)
        h = torch.cat([style_token, h], dim=1)  # prepend style token

        # Transformer layers with FiLM conditioning
        for enc_layer, cond_layer in zip(self.encoder_layers, self.style_conditioners):
            h = enc_layer(h)
            h = cond_layer(h, style_emb)

        # Remove the prepended style token before classification
        h = h[:, 1:, :]
        return self.chord_head(h)
