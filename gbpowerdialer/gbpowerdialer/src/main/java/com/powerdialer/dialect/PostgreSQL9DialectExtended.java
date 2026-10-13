package com.powerdialer.dialect;

import java.sql.Types;

import org.hibernate.type.NumericBooleanType;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptor;

/**
 * Custom dialect for handling boolean java type as int2 in database
 */
public class PostgreSQL9DialectExtended extends org.hibernate.dialect.PostgreSQL9Dialect {

  public PostgreSQL9DialectExtended() {
    super();
    registerColumnType(Types.BOOLEAN, "int2");
    registerColumnType(Types.BIT, "int2");
  }

  @Override
  public SqlTypeDescriptor getSqlTypeDescriptorOverride(int sqlCode) {
    if (sqlCode == Types.BOOLEAN) {
      return NumericBooleanType.INSTANCE.getSqlTypeDescriptor();
    }
    return super.getSqlTypeDescriptorOverride(sqlCode);
  }

}
