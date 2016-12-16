## Intro
This is migration tool is used to import spreedSheets into DynamoDb.

The migration has its own database configuration in order not to accidentally write to an important database (``` app/src/test/scala/com/officeserve/basketservice/migration/config/migration.conf```). 
For the same reason you need to provide the amazon credentials as jvm args everytime you are running the script.  I recommend strongly not to save these credentials anywhere (for example: in Intellij run configuration)

I have added a template files for the migrations(```app/src/test/scala/com/officeserve/basketservice/migration/templates ```). 
**Please note any change to the structure of these template requires a revisit of the relevant CellMapping Object**
 
## Running 
 * run ``` sbt "project app" "test:run <accesssKey> <secretKey> <MigrationfilePath/MigrationfileName.xlsx> " ```
 * choose the migration type. For now options are OnAccount or Postcodes
 
 
 
