"""
Minimal transforms shim for pytorch3d.
Always provides a small subset implemented with PyTorch only, avoiding
self-import/proxy loops which can happen if this shim is first on PYTHONPATH.

Note: PyTorch3D convention for quaternions is (x, y, z, w) (scalar last).
"""
from __future__ import annotations

import torch


def _normalize_quaternion(q: torch.Tensor, eps: float = 1e-8) -> torch.Tensor:
    """Normalize quaternions q (..., 4) to unit length."""
    return q / (q.norm(dim=-1, keepdim=True).clamp_min(eps))


def quaternion_to_matrix(quaternions: torch.Tensor) -> torch.Tensor:
    """
    Convert quaternions to rotation matrices.
    Input is in (x, y, z, w) with scalar last; output is (..., 3, 3).
    """
    q = _normalize_quaternion(quaternions)
    x, y, z, w = q.unbind(-1)
    # Compute products
    xx, yy, zz = x * x, y * y, z * z
    xy, xz, yz = x * y, x * z, y * z
    wx, wy, wz = w * x, w * y, w * z

    one = torch.ones_like(w)
    two = one + one

    m00 = one - two * (yy + zz)
    m01 = two * (xy - wz)
    m02 = two * (xz + wy)

    m10 = two * (xy + wz)
    m11 = one - two * (xx + zz)
    m12 = two * (yz - wx)

    m20 = two * (xz - wy)
    m21 = two * (yz + wx)
    m22 = one - two * (xx + yy)

    rot = torch.stack(
        [
            torch.stack([m00, m01, m02], dim=-1),
            torch.stack([m10, m11, m12], dim=-1),
            torch.stack([m20, m21, m22], dim=-1),
        ],
        dim=-2,
    )
    return rot


def matrix_to_quaternion(R: torch.Tensor, eps: float = 1e-8) -> torch.Tensor:
    """
    Convert rotation matrices to quaternions (x, y, z, w), scalar last.
    Accepts R of shape (..., 3, 3).
    """
    if R.shape[-2:] != (3, 3):
        raise ValueError("matrix_to_quaternion expects (..., 3, 3) input")

    m00 = R[..., 0, 0]
    m11 = R[..., 1, 1]
    m22 = R[..., 2, 2]

    trace = m00 + m11 + m22

    w = torch.sqrt(torch.clamp(trace + 1.0, min=0.0)) / 2.0
    x = torch.sqrt(torch.clamp(1.0 + m00 - m11 - m22, min=0.0)) / 2.0
    y = torch.sqrt(torch.clamp(1.0 - m00 + m11 - m22, min=0.0)) / 2.0
    z = torch.sqrt(torch.clamp(1.0 - m00 - m11 + m22, min=0.0)) / 2.0

    # Determine signs using off-diagonal elements
    x = x.copysign((R[..., 2, 1] - R[..., 1, 2]))
    y = y.copysign((R[..., 0, 2] - R[..., 2, 0]))
    z = z.copysign((R[..., 1, 0] - R[..., 0, 1]))

    q = torch.stack([x, y, z, w], dim=-1)
    return _normalize_quaternion(q)


__all__ = ['quaternion_to_matrix', 'matrix_to_quaternion']
