package validation;
import java.io.FileInputStream;
import com.threerings.export.BinaryImporter;
public class InspectPlayerModel {
 public static void main(String[] args) throws Exception {
  try(FileInputStream input=new FileInputStream("rsrc/character/pc/model_level.dat")) {
   CombatDataAudit.dump(new BinaryImporter(input).qe(),0,"playerModel");
  }
 }
}
