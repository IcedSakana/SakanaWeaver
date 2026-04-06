"""
SakanaWeaver AI Engine — Melody Generation Module

Transformer-based melody generation conditioned on:
- Key signature & scale
- Chord progression
- Rhythmic constraints
- Style embeddings
"""

import torch
import torch.nn as nn
from dataclasses import dataclass


@dataclass
class MelodyConfig:
    vocab_size: int = 128         # MIDI pitch range
    max_seq_len: int = 1024       # max sequence length (in ticks/tokens)
    d_model: int = 512
    n_heads: int = 8
    n_layers: int = 6
    d_ff: int = 2048
    dropout: float = 0.1


class MelodyTransformer(nn.Module):
    """Autoregressive Transformer for melody generation."""

    def __init__(self, config: MelodyConfig):
        super().__init__()
        self.config = config

        self.pitch_embedding = nn.Embedding(config.vocab_size, config.d_model)
        self.position_embedding = nn.Embedding(config.max_seq_len, config.d_model)

        encoder_layer = nn.TransformerEncoderLayer(
            d_model=config.d_model,
            nhead=config.n_heads,
            dim_feedforward=config.d_ff,
            dropout=config.dropout,
            batch_first=True,
        )
        self.transformer = nn.TransformerEncoder(encoder_layer, num_layers=config.n_layers)
        self.output_head = nn.Linear(config.d_model, config.vocab_size)

    def forward(self, x: torch.Tensor, mask: torch.Tensor | None = None) -> torch.Tensor:
        seq_len = x.size(1)
        positions = torch.arange(seq_len, device=x.device).unsqueeze(0)

        h = self.pitch_embedding(x) + self.position_embedding(positions)

        if mask is None:
            mask = nn.Transformer.generate_square_subsequent_mask(seq_len, device=x.device)

        h = self.transformer(h, mask=mask)
        return self.output_head(h)

    @torch.no_grad()
    def generate(self, prompt: torch.Tensor, max_len: int = 256, temperature: float = 1.0) -> torch.Tensor:
        """Autoregressive generation from a prompt."""
        self.eval()
        generated = prompt.clone()

        for _ in range(max_len):
            logits = self.forward(generated)[:, -1, :] / temperature
            probs = torch.softmax(logits, dim=-1)
            next_token = torch.multinomial(probs, num_samples=1)
            generated = torch.cat([generated, next_token], dim=1)

        return generated
