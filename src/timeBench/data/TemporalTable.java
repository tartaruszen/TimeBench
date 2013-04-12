package timeBench.data;

import prefuse.data.DataTypeException;
import prefuse.data.Table;
import prefuse.data.column.Column;
import prefuse.data.column.ExpressionColumn;
import timeBench.data.expression.TemporalColumnExpression;

public class TemporalTable extends Table {
    
    public static final String ID_POSTFIX = "_id";

    public void addTemporalColumn(String name, TemporalElementStore store) {
        String idColumn = name + ID_POSTFIX; 
        this.addColumn(idColumn, long.class, -1l);
        this.addColumn(name, new TemporalColumn(this, idColumn, store));
        // TODO index idColumn?
        // TODO store.register(tmpds);
    }

    @Override
    protected Column removeColumn(int idx) {
        Column col = this.getColumn(idx);
        if (col instanceof TemporalColumn) {
//            ((TemporalColumn)col).unregister();
            // TODO store.unregister(this);
            
            String idColumn = super.getColumnName(idx) + ID_POSTFIX;
            super.removeColumn(idColumn);
        }
        return super.removeColumn(idx);
    }

    static class TemporalColumn extends ExpressionColumn {
        
        // Alternatively it would be possible to extend AbstractColumn directly
        // but in that case the caching would need to be replicated
        
        Table table;
        TemporalElementStore store;
        String idColumn;

        public TemporalColumn(Table table, String idColumn, TemporalElementStore store) {
            super(table, new TemporalColumnExpression(idColumn, store, true));
            this.table = table;
            this.store = store;
            this.idColumn = idColumn;
        }
        
        @SuppressWarnings({ "unchecked", "rawtypes" })
        @Override
        public boolean canSet(Class type) {
            return type.isAssignableFrom(TemporalElement.class);
        }

        @Override
        public void set(Object val, int row) throws DataTypeException {
            if (val instanceof TemporalElement) {
                TemporalElement te = (TemporalElement) val;
                if (te.getTemporalElementStore() == store) {
                    table.setLong(row, idColumn, te.getId());
                } else {
                     throw new DataTypeException(
                             "Cannot set temporal element from different data store.");
                }
            } else {
                throw new DataTypeException(
                        "Cannot set temporal element from type " + val.getClass() + ".");
            }
        }

        // XXX problem: TE in cache; TE invalidated & replaced; how update cache? (a) check on get (b) invalidate in Store (c) no cache
        @Override
        public TemporalElement get(int row) {
            // workaround to empty cache if temp. el. was removed
            TemporalElement te = (TemporalElement) super.get(row);
            if (! te.isValid()) {
                super.invalidateCache(row, row);
                te = (TemporalElement) super.get(row);
            }
            return te;
        }

        // XXX why?
//        @Override
//        public String getString(int row) throws DataTypeException {
//            // workaround to empty cache if temp. el. was removed
//            TemporalElement te = (TemporalElement) super.get(row);
//            if (! te.isValid()) {
//                super.invalidateCache(row, row);
//            }
//            return super.getString(row);
//        }
    }
}
