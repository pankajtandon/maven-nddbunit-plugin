package com.nayidisha.plugins.nddbunit;

import java.io.File;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.database.ForwardOnlyResultSetTableFactory;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.IMetadataHandler;
import org.dbunit.dataset.datatype.IDataTypeFactory;

import com.nayidisha.plugins.nddbunit.schema.AutoExportConfigDocument;
import com.nayidisha.plugins.nddbunit.schema.ConfigDocument;
import com.nayidisha.plugins.nddbunit.schema.ExportConfigDocument;
import com.nayidisha.plugins.nddbunit.schema.AutoExportConfigListDocument.AutoExportConfigList;
import com.nayidisha.plugins.nddbunit.schema.ExportConfigListDocument.ExportConfigList;

/**
 * Copied directly from dbunit-maven-plugin source
 * 
 * @author Pankaj Tandon
 * 
 */
public abstract class AbstractDBUnitMojo extends AbstractMojo {

	/**
	 * The class name of the JDBC driver to be used.
	 * 
	 * @parameter expression="${driver}"
	 * @required
	 * @since 1.0.0
	 */
	protected String driver;

	/**
	 * Database username. If not given, it will be looked up through
	 * settings.xml's server with ${settingsKey} as key
	 * 
	 * @parameter expression="${username}"
	 * @since 1.0.0
	 */
	protected String username;

	/**
	 * Database password. If not given, it will be looked up through
	 * settings.xml's server with ${settingsKey} as key
	 * 
	 * @parameter expression="${password}"
	 * @since 1.0.0
	 */
	protected String password;

	/**
	 * The JDBC URL for the database to access, e.g. jdbc:db2:SAMPLE.
	 * 
	 * @parameter
	 * @required expression="${url}"
	 * @since 1.0.0
	 */
	protected String url;


	/**
	 * Set the DataType factory to add support for non-standard database vendor
	 * data types.
	 * 
	 * @parameter expression="${dataTypeFactoryName}"
	 *            default-value="org.dbunit.dataset.datatype.DefaultDataTypeFactory"
	 * @since 1.0.0
	 */
	protected String dataTypeFactoryName = "org.dbunit.dataset.datatype.DefaultDataTypeFactory";

	/**
	 * escapePattern
	 * 
	 * @parameter expression="${escapePattern}"
	 * @since 1.0.0
	 */
	protected String escapePattern;


	/**
	 * Skip the execution when true, very handy when using together with
	 * maven.test.skip.
	 * 
	 * @parameter expression="${skip}" default-value="false"
	 * @since 1.0.0
	 */
	protected boolean skip;


	/**
	 * Class name of metadata handler.
	 * 
	 * @parameter expression="${metadataHandlerName}"
	 *            default-value="org.dbunit.database.DefaultMetadataHandler"
	 * @since 1.0.0
	 */
	protected String metadataHandlerName;

	/**
	 * DataSet file format that can be specified all exportConfig. Overridden by
	 * the value specified by a specific exportConfig.
	 * 
	 * @parameter expression="${format}" default-value="xml"
	 * @since 1.0.0
	 */
	protected String format;
	
	/**
	 * Key for license
	 * @parameter
	 */
	protected String licenseKey;
	
	/**
	 * Fully qualified file path to the file that contains the autoExport and export configurations.
	 * @parameter 
	 * @required
	 * @since 2.0.0
	 */
	protected File configFile;
	
    /**
     * doctype
     * @parameter expression="${doctype}"
     */
    protected String doctype;
    
    /**
     * Set to true to order exported data according to integrity constraints defined in DB.
     * @parameter
     */
    protected boolean ordered;
    
    /**
     * Encoding of exported data.
     * @parameter expression="${encoding}" default-value="${project.build.sourceEncoding}"
     */
    protected String encoding;
	

	/**
	 * <p>
	 * The Id of an ExportConfig or an AutoExportConfig that needs to be executed. Note that this id
	 * must exist in one of the configured ExportConfigs or AutoExportConfigs
	 * </p>
	 * 
	 * @parameter expression="${idToExport}"
	 * 
	 */
	protected String idToExport;
	
	private String LICENSE_EXPIRATION_DATE = "12/31/2011";
	private String LICENSE_KEY = "THODRURWKEJLDSLPSKL7303730JKIY58845$";
			
	public void execute() throws MojoExecutionException, MojoFailureException {
		checkExpiration();
	}

	IDatabaseConnection createConnection() throws Exception {

		// Instantiate JDBC driver
		Class dc = Class.forName(driver);
		Driver driverInstance = (Driver) dc.newInstance();
		Properties info = new Properties();
		info.put("user", username);

		if (password != null) {
			info.put("password", password);
		}

		Connection conn = driverInstance.connect(url, info);

		if (conn == null) {
			// Driver doesn't understand the URL
			throw new SQLException("No suitable Driver for " + url);
		}
		conn.setAutoCommit(true);
		//Convert username (or schema) to UC to prevent a DBUnit warning...
		IDatabaseConnection connection = new DatabaseConnection(conn, this.username.toUpperCase());
		DatabaseConfig config = connection.getConfig();
		config.setFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS,
				false);
		config.setFeature(DatabaseConfig.FEATURE_QUALIFIED_TABLE_NAMES,
				false);
		config.setFeature(DatabaseConfig.FEATURE_DATATYPE_WARNING,
				false);
		config.setFeature(DatabaseConfig.FEATURE_SKIP_ORACLE_RECYCLEBIN_TABLES,
				false);

		config.setProperty(DatabaseConfig.PROPERTY_ESCAPE_PATTERN,
				escapePattern);
		config.setProperty(DatabaseConfig.PROPERTY_RESULTSET_TABLE_FACTORY,
				new ForwardOnlyResultSetTableFactory());

		// Setup data type factory
		IDataTypeFactory dataTypeFactory = (IDataTypeFactory) Class.forName(
				dataTypeFactoryName).newInstance();
		config.setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY,
				dataTypeFactory);

		// Setup metadata handler
		IMetadataHandler metadataHandler = (IMetadataHandler) Class.forName(
				metadataHandlerName).newInstance();
		config.setProperty(DatabaseConfig.PROPERTY_METADATA_HANDLER,
				metadataHandler);

		return connection;
	}



	private void checkExpiration() throws MojoExecutionException {
		Date today = new Date();
		
		Date expiration = null;
		try {
			expiration = new SimpleDateFormat("MM/dd/yyyy").parse(LICENSE_EXPIRATION_DATE);
		}catch (Exception e){
			//will never happen
		}
		if(today.after(expiration)){	
			if ((!StringUtils.isEmpty(this.licenseKey)) ) {
				if (!this.LICENSE_KEY.equals(this.licenseKey)){
					throw new MojoExecutionException("License key not valid. Please contact info@nayidisha.com for renewal.");
				}
			} else {
				throw new MojoExecutionException("License expired. Please contact info@nayidisha.com for renewal.");
			}
		}
	}
	
	
	/*
	 * Too long a file name tickles this bug:
	 * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4812991
	 * 
	 * That is why I had to create this mess:
	 */
	protected File createFile(String path) throws Exception {
		String dirPart = this.getFileDirectory(path);
		String filePart = this.getFileName(path);
		this.getLog().debug("DirPart:" + dirPart);
		this.getLog().debug("filePart:" + filePart);
		FileUtils.forceMkdir(new File(dirPart));
		File file = new File(dirPart, filePart);
		if (!file.exists()) {
			file.createNewFile();
		}
		return file;
	}
	

	protected File createFileWithSuffix(String path, String suffix) throws Exception {
		String dirPart = this.getFileDirectory(path);
		String filePart = this.getFileName(path);
		int j = filePart.lastIndexOf(".");
		String fp = filePart;
		if (j > 0){
			fp = filePart.substring(0, j) + suffix + filePart.substring(j);
		} else {
			fp = filePart + suffix;
		}
		
		this.getLog().debug("DirPart:" + dirPart);
		this.getLog().debug("filePart:" + fp);
		FileUtils.forceMkdir(new File(dirPart));
		File file = new File(dirPart, fp);
		if (!file.exists()) {
			file.createNewFile();
		}
		return file;
	}
	
	public String getFileDirectory(String fileOrPath){
		String sep = File.separator;
		fileOrPath = StringUtils.replace(fileOrPath, "\\", sep);
		fileOrPath = StringUtils.replace(fileOrPath, "/", sep);
		
		this.getLog().debug("Path after separator replacement:" + fileOrPath);
		int i = fileOrPath.lastIndexOf(sep);
		String dirPart = "";
		if (i > 0){
			dirPart = fileOrPath.substring(0, i);
		} else {
			dirPart = ".";
		}
		
		return dirPart;
	}
	
	public String getFileName(String fileOrPath){
		String sep = File.separator;
		fileOrPath = StringUtils.replace(fileOrPath, "\\", sep);
		fileOrPath = StringUtils.replace(fileOrPath, "/", sep);
		
		this.getLog().debug("Path after separator replacement:" + fileOrPath);
		int i = fileOrPath.lastIndexOf(sep);
		String filePart = "";
		if (i > 0){
			filePart = fileOrPath.substring(i+1);
		} else {
			filePart = fileOrPath;
		}
		
		return filePart;
	}
	
	protected AutoExportConfigDocument.AutoExportConfig[] readAutoExportConfig(File file) throws MojoExecutionException{
		AutoExportConfigDocument.AutoExportConfig[] aecArray = null;
		try {
			ConfigDocument configDoc = ConfigDocument.Factory.parse(file);
			AutoExportConfigList aecl = configDoc.getConfig().getAutoExportConfigList();
			aecArray = aecl.getAutoExportConfigArray();
		} catch (Exception e) {
			throw new MojoExecutionException("Error processing file at " + file.toString(), e);
		}
		return aecArray;
	}

	protected ExportConfigDocument.ExportConfig[] readExportConfig(File file)throws MojoExecutionException{
		ExportConfigDocument.ExportConfig[] ecArray = null;
		try {
			ConfigDocument configDoc = ConfigDocument.Factory.parse(file);
			ExportConfigList ecl = configDoc.getConfig().getExportConfigList();
			ecArray = ecl.getExportConfigArray();
		} catch (Exception e) {
			throw new MojoExecutionException("Error processing file at " + file.toString(), e);
		}
		return ecArray;
	}
}
