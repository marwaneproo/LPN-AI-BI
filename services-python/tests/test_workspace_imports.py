def test_workspace_imports() -> None:
    import data_import
    import eval
    import predictive
    import schema_retrieval
    import sql_validator

    assert data_import is not None
    assert eval is not None
    assert predictive is not None
    assert schema_retrieval is not None
    assert sql_validator is not None
