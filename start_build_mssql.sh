./oltpbenchmark -b tpcc -c tpcc_mssql_build.xml --runscript config/drop_build_mssql.sql
./oltpbenchmark -b tpcc -c tpcc_mssql_build.xml --clear=true --create=true --load=true 
