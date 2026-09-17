package db.migration;

import app.listful.security.TokenHashing;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V13__hash_public_share_tokens extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement select = context.getConnection().createStatement();
             ResultSet rows = select.executeQuery("select id, share_token from lists where share_token is not null")) {
            while (rows.next()) {
                String id = rows.getString("id");
                String token = rows.getString("share_token");
                if (TokenHashing.isSha256Hash(token)) {
                    continue;
                }
                try (PreparedStatement update = context.getConnection().prepareStatement(
                    "update lists set share_token = ? where id = ?")) {
                    update.setString(1, TokenHashing.sha256(token));
                    update.setString(2, id);
                    update.executeUpdate();
                }
            }
        }
    }
}
