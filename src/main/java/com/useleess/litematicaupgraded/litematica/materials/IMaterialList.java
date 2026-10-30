package com.useleess.litematicaupgraded.litematica.materials;

import java.util.List;

import com.useleess.litematicaupgraded.litematica.util.value.BlockInfoListType;

public interface IMaterialList
{
    BlockInfoListType getMaterialListType();

    void setMaterialListEntries(List<MaterialListEntry> list);
}
