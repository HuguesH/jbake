package org.jbake.app;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

/**
 * Created by Hugues on 21/03/2015.
 */
public class FileUtilTest {

    @Test
    public void copyDirectoryTestWithFilter() throws Exception{
        FileUtils.deleteDirectory(new File("./target/test"));
        FileUtil.copyDirectory(new File("./src/test/resources/content"), new File("./target/test"), new String[]{".png"} );
        Assert.assertTrue(new File("./target/test/papers/glyphicons-halflings.png").exists());
        Assert.assertFalse(new File("./target/test/projects.html").exists());
    }

    @Test
    public void copyDirectoryTestWithFilterBlank() throws Exception{
        FileUtils.deleteDirectory(new File("./target/test"));
        FileUtil.copyDirectory(new File("./src/test/resources/content"), new File("./target/test"), new String[]{""} );
        Assert.assertFalse(new File("./target/test/papers/glyphicons-halflings.png").exists());
        Assert.assertFalse(new File("./target/test/projects.html").exists());
    }
}


