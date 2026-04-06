"""
SakanaWeaver AI Engine — Melody Generation Module

Transformer-based melody generation conditioned on:
- Key signature & scale
- Chord progression
- Rhythmic constraints
- Style embeddings (FiLM conditioning per layer)
"""

import torch
import torch.nn as nn
from dataclasses import dataclass

from ..style_embedding import StyleEncoder, StyleConditioner, StyleEmbeddingConfig


@dataclass
class MelodyConfig:
    vocab_size: int = 128         # MIDI pitch range
    max_seq_len: int = 1024       # max sequence length (in ticks/tokens)
    d_model: int = 512
    n_heads: int = 8
    n_layers: int = 6
    d_ff: int = 2048
    dropout: float = 0.1
    style_dim: int = 128          # style embedding dimension


class StyleConditionedTransformerLayer(nn.Module):
    """Transformer encoder layer with FiLM-based style conditioning injected after self-attention."""

    def __init__(self, d_model: int, n_heads: int, d_ff: int, dropout: float, style_dim: int):
        super().__init__()
        self.self_attn = nn.MultiheadAttention(d_model, n_heads, dropout=dropout, batch_first=True)
        self.norm1 = nn.LayerNorm(d_model)
        self.style_cond = StyleConditioner(style_dim, d_model)
        self.ff = nn.Sequential(
            nn.Linear(d_model, d_ff),
            nn.GELU(),
            nn.Dropout(dropout),
            nn.Linear(d_ff, d_model),
            nn.Dropout(dropout),
        )
        self.norm2 = nn.LayerNorm(d_model)
        self.dropout = nn.Dropout(dropout)

    def forward(self, x: torch.Tensor, style_emb: torch.Tensor, mask: torch.Tensor | None = None) -> torch.Tensor:
        # Self-attention
        attn_out, _ = self.self_attn(x, x, x, attn_mask=mask)
        x = self.norm1(x + self.dropout(attn_out))
        # Style conditioning via FiLM
        x = self.style_cond(x, style_emb)
        # Feed-forward
        x = self.norm2(x + self.ff(x))
        return x


class MelodyTransformer(nn.Module):
    """Autoregressive Transformer for style-conditioned melody generation."""

    def __init__(self, config: MelodyConfig):
        super().__init__()
        self.config = config

        self.pitch_embedding = nn.Embedding(config.vocab_size, config.d_model)
        self.position_embedding = nn.Embedding(config.max_seq_len, config.d_model)

        # Style encoder (shared or per-model)
        self.style_encoder = StyleEncoder(StyleEmbeddingConfig(style_dim=config.style_dim))

        # Style-conditioned transformer layers
        self.layers = nn.ModuleList([
            StyleConditionedTransformerLayer(
                config.d_model, config.n_heads, config.d_ff, config.dropout, config.style_dim,
            )
            for _ in range(config.n_layers)
        ])
        self.output_head = nn.Linear(config.d_model, config.vocab_size)

    def forward(
        self,
        x: torch.Tensor,
        style_id: torch.Tensor | None = None,
        style_features: torch.Tensor | None = None,
        mask: torch.Tensor | None = None,
    ) -> torch.Tensor:
        seq_len = x.size(1)
        positions = torch.arange(seq_len, device=x.device).unsqueeze(0)

        h = self.pitch_embedding(x) + self.position_embedding(positions)

        if mask is None:
            mask = nn.Transformer.generate_square_subsequent_mask(seq_len, device=x.device)

        # Encode style
        style_emb = self.style_encoder(style_id=style_id, continuous_features=style_features)

        # Pass through style-conditioned layers
        for layer in self.layers:
            h = layer(h, style_emb, mask)

        return self.output_head(h)

    @torch.no_grad()
    def generate(
        self,
        prompt: torch.Tensor,
        style_id: torch.Tensor | None = None,
        style_features: torch.Tensor | None = None,
        max_len: int = 256,
        temperature: float = 1.0,
        top_k: int | None = None,
    ) -> torch.Tensor:
        """Autoregressive style-conditioned melody generation."""
        self.eval()
        generated = prompt.clone()

        for _ in range(max_len):
            logits = self.forward(generated, style_id=style_id, style_features=style_features)[:, -1, :]
            logits = logits / temperature

            # Optional top-k filtering
            if top_k is not None:
                topk_vals, _ = torch.topk(logits, top_k)
                logits[logits < topk_vals[:, -1:]] = float('-inf')

            probs = torch.softmax(logits, dim=-1)
            next_token = torch.multinomial(probs, num_samples=1)
            generated = torch.cat([generated, next_token], dim=1)

        return generated
