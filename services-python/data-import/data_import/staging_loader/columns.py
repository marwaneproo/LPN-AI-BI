"""Column-name standardization and value coercion.

The standardizer matches the behaviour of the legacy ETL's
``standardize_column_name`` (DataWareHouse/processus_de_vente/etl/scripts/
extract_sources.py) so source headers map onto the BI-06 staging column names:
``C_REGION_ID`` -> ``c_region_id``.
"""

from __future__ import annotations

import re

_NON_ALNUM = re.compile(r"[^a-z0-9]+")
_MULTI_UNDERSCORE = re.compile(r"_+")


def standardize_column_name(name: object) -> str:
    """Lower-case, snake-case, and trim a source header to a staging column name."""

    value = str(name).strip().lower()
    value = _NON_ALNUM.sub("_", value)
    value = _MULTI_UNDERSCORE.sub("_", value).strip("_")
    return value


# Literal null sentinel emitted by the Toad CSV export. DW-04: M_PRODUCT_PO.csv
# writes the bare token ``NULL`` for empty cells (every row); no other file in the
# package uses ``NULL`` as a real value, so mapping it to SQL NULL is the correct
# interpretation, not data loss. xlsx never emits this token (empty cells are
# native ``None``).
_NULL_TOKENS = frozenset({"NULL"})


def coerce_value(value: object) -> object:
    """Trim strings and convert blanks / null-tokens to ``None``; pass others through.

    openpyxl read_only yields native Python types (int/float/datetime/None);
    csv yields strings. Both are normalised here so empty cells become SQL NULL.
    The literal CSV null sentinel ``NULL`` (see :data:`_NULL_TOKENS`) is also
    mapped to ``None``.
    """

    if value is None:
        return None
    if isinstance(value, str):
        stripped = value.strip()
        if stripped == "" or stripped in _NULL_TOKENS:
            return None
        return stripped
    return value
