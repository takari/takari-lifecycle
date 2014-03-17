package record.reference;

import missing.*;

class MissingImportOnDemand {
  // 'Name' can be resolved to 
  // - 'missing.Name'
  // - 'record.reference.Name'
  // - 'java.lang.Name'
  Name name;
}
