"""
SakanaWeaver AI Engine — Drum Pattern Generation Module

VAE-based drum pattern generator.
Encodes/decodes fixed-length drum patterns (e.g., 1-2 bars).
"""

import torch
import torch.nn as nn
from dataclasses import dataclass


@dataclass
class DrumConfig:
    num_instruments: int = 9      # kick, snare, hihat_c, hihat_o, tom1, tom2, crash, ride, clap
    steps_per_bar: int = 16
    num_bars: int = 2
    latent_dim: int = 64
    hidden_dim: int = 256


class DrumPatternVAE(nn.Module):
    """Variational Autoencoder for drum pattern generation."""

    def __init__(self, config: DrumConfig):
        super().__init__()
        self.config = config
        input_dim = config.num_instruments * config.steps_per_bar * config.num_bars

        # Encoder
        self.encoder = nn.Sequential(
            nn.Linear(input_dim, config.hidden_dim),
            nn.ReLU(),
            nn.Linear(config.hidden_dim, config.hidden_dim),
            nn.ReLU(),
        )
        self.mu = nn.Linear(config.hidden_dim, config.latent_dim)
        self.log_var = nn.Linear(config.hidden_dim, config.latent_dim)

        # Decoder
        self.decoder = nn.Sequential(
            nn.Linear(config.latent_dim, config.hidden_dim),
            nn.ReLU(),
            nn.Linear(config.hidden_dim, config.hidden_dim),
            nn.ReLU(),
            nn.Linear(config.hidden_dim, input_dim),
            nn.Sigmoid(),
        )

    def encode(self, x: torch.Tensor):
        h = self.encoder(x)
        return self.mu(h), self.log_var(h)

    def reparameterize(self, mu: torch.Tensor, log_var: torch.Tensor) -> torch.Tensor:
        std = torch.exp(0.5 * log_var)
        eps = torch.randn_like(std)
        return mu + eps * std

    def decode(self, z: torch.Tensor) -> torch.Tensor:
        return self.decoder(z)

    def forward(self, x: torch.Tensor):
        mu, log_var = self.encode(x)
        z = self.reparameterize(mu, log_var)
        return self.decode(z), mu, log_var

    @torch.no_grad()
    def generate(self, num_patterns: int = 1) -> torch.Tensor:
        """Sample random drum patterns from the latent space."""
        self.eval()
        z = torch.randn(num_patterns, self.config.latent_dim)
        patterns = self.decode(z)
        return (patterns > 0.5).float().view(
            num_patterns,
            self.config.num_bars,
            self.config.steps_per_bar,
            self.config.num_instruments,
        )
