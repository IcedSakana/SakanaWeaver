"""
SakanaWeaver AI Engine — Drum Pattern Generation Module

Conditional VAE-based drum pattern generator.
Encodes/decodes fixed-length drum patterns (e.g., 1-2 bars),
conditioned on style embedding.
"""

import torch
import torch.nn as nn
from dataclasses import dataclass

from ..style_embedding import StyleEncoder, StyleEmbeddingConfig


@dataclass
class DrumConfig:
    num_instruments: int = 9      # kick, snare, hihat_c, hihat_o, tom1, tom2, crash, ride, clap
    steps_per_bar: int = 16
    num_bars: int = 2
    latent_dim: int = 64
    hidden_dim: int = 256
    style_dim: int = 128


class DrumPatternVAE(nn.Module):
    """Conditional VAE: style → drum pattern."""

    def __init__(self, config: DrumConfig):
        super().__init__()
        self.config = config
        input_dim = config.num_instruments * config.steps_per_bar * config.num_bars

        # Style encoder
        self.style_encoder = StyleEncoder(StyleEmbeddingConfig(style_dim=config.style_dim))

        # Encoder: pattern + style → latent
        encoder_input_dim = input_dim + config.style_dim
        self.encoder = nn.Sequential(
            nn.Linear(encoder_input_dim, config.hidden_dim),
            nn.ReLU(),
            nn.Linear(config.hidden_dim, config.hidden_dim),
            nn.ReLU(),
        )
        self.mu = nn.Linear(config.hidden_dim, config.latent_dim)
        self.log_var = nn.Linear(config.hidden_dim, config.latent_dim)

        # Decoder: latent + style → pattern
        decoder_input_dim = config.latent_dim + config.style_dim
        self.decoder = nn.Sequential(
            nn.Linear(decoder_input_dim, config.hidden_dim),
            nn.ReLU(),
            nn.Linear(config.hidden_dim, config.hidden_dim),
            nn.ReLU(),
            nn.Linear(config.hidden_dim, input_dim),
            nn.Sigmoid(),
        )

    def encode(self, x: torch.Tensor, style_emb: torch.Tensor):
        h = self.encoder(torch.cat([x, style_emb], dim=-1))
        return self.mu(h), self.log_var(h)

    def reparameterize(self, mu: torch.Tensor, log_var: torch.Tensor) -> torch.Tensor:
        std = torch.exp(0.5 * log_var)
        eps = torch.randn_like(std)
        return mu + eps * std

    def decode(self, z: torch.Tensor, style_emb: torch.Tensor) -> torch.Tensor:
        return self.decoder(torch.cat([z, style_emb], dim=-1))

    def forward(
        self,
        x: torch.Tensor,
        style_id: torch.Tensor | None = None,
        style_features: torch.Tensor | None = None,
    ):
        style_emb = self.style_encoder(style_id=style_id, continuous_features=style_features)
        mu, log_var = self.encode(x, style_emb)
        z = self.reparameterize(mu, log_var)
        return self.decode(z, style_emb), mu, log_var

    @torch.no_grad()
    def generate(
        self,
        num_patterns: int = 1,
        style_id: torch.Tensor | None = None,
        style_features: torch.Tensor | None = None,
    ) -> torch.Tensor:
        """Generate drum patterns conditioned on style."""
        self.eval()
        style_emb = self.style_encoder(style_id=style_id, continuous_features=style_features)
        if style_emb.size(0) == 1 and num_patterns > 1:
            style_emb = style_emb.expand(num_patterns, -1)
        z = torch.randn(num_patterns, self.config.latent_dim, device=style_emb.device)
        patterns = self.decode(z, style_emb)
        return (patterns > 0.5).float().view(
            num_patterns,
            self.config.num_bars,
            self.config.steps_per_bar,
            self.config.num_instruments,
        )
