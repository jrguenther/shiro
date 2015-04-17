package org.shirolang.functions.data;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import org.shirolang.base.SFunc;
import org.shirolang.base.SFuncBase;
import org.shirolang.base.TypedValue;
import org.shirolang.values.SString;

import java.util.Map;

/**
 *
 *
 */
public class SelectColumnMFunc extends SFuncBase {
    private static final String TYPE = "SelectColumn";
    private static final String TABLE = "table";
    private static final String COLUMN = "column";

    private static final String NEW_TABLE = "newTable";

    public SelectColumnMFunc(){
        super();

        args.setKeyForIndex(TABLE, 0);
        args.add(new TypedValue("Table"));

        args.setKeyForIndex(COLUMN, 1);
        args.add(new TypedValue("String"));

        results.setKeyForIndex(NEW_TABLE, 0);
        results.add(new TypedValue("Table"));
    }

    @Override
    public void evaluate() {
        SFunc table = getArg(TABLE).getResult();
        SFunc column = getArg(COLUMN).getResult();

        Table<Integer, String, Comparable> tableValue = ((STable) table).getValue();
        String columnSelected = ((SString) column).getValue();

        Table<Integer, String, Comparable> tableMatches = HashBasedTable.<Integer, String, Comparable> create();
        Map<Integer, Comparable> columnMatch = tableValue.column(columnSelected);

        for(Integer i: columnMatch.keySet()){
            tableMatches.put(i, columnSelected, columnMatch.get(i));
        }

        STable t = new STable(tableMatches);
        t.evaluate();
        setResult(NEW_TABLE, t);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public int getMaxArgs() {
        return 1;
    }

    @Override
    public int getMinArgs() {
        return 1;
    }
}
