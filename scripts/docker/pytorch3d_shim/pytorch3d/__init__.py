# Lightweight pytorch3d shim package.
# Always expose local transforms to avoid recursion/self-import issues when the shim
# is first on PYTHONPATH.

from . import transforms  # type: ignore

__all__ = ['transforms']
