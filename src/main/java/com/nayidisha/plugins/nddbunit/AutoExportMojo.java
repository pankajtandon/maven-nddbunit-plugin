package com.nayidisha.plugins.nddbunit;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.StringUtils;
import org.dbunit.database.CachedResultSetTableFactory;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.QueryDataSet;
import org.dbunit.dataset.CompositeDataSet;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.XmlDataSet;
import org.dbunit.util.QualifiedTableName;

import com.nayidisha.plugins.nddbunit.schema.AutoExportConfigDocument.AutoExportConfig;
import com.nayidisha.plugins.nddbunit.schema.ExcludedTableListDocument.ExcludedTableList;

/**
 * @goal autoExport
 * @author tandonp
 *
 */
public class AutoExportMojo extends AbstractDBUnitMojo {


	
	private boolean useQuotedValues;
	
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (skip) {
			this.getLog().info("Skip autoExport execution");
			return;
		}

		super.execute();

		Hashtable<String, AutoExportConfig> hash = checkCorrectnessOfAutoExportConfigAndBuildHash();
		if (StringUtils.isEmpty(idToExport)) {
			idToExport = this.listAutoExportConfigs(hash, true);
		}
		if (!StringUtils.isEmpty(idToExport)) {
			AutoExportConfig autoExportConfigToExecute = (AutoExportConfig) hash
					.get(idToExport);

			try {
				if (autoExportConfigToExecute != null) {	
					this.getLog().info( "Accessing URL: " + url +  " as user " + this.username + "...");
					this.setUseQuotedValues(autoExportConfigToExecute.getUseQuotedValues());
					IDatabaseConnection connection = createConnection();
					try {

						String table = autoExportConfigToExecute.getBaseTable();
						table = table.toUpperCase();
						String whereClause = autoExportConfigToExecute
								.getWhereClause();

						Hashtable<Integer, Hashtable<String, TableNode>> levelHash = processTables(connection,
								table, whereClause);

						List<IDataSet> completeQueryDataSetList = new ArrayList<IDataSet>();
						boolean singleOutputFile = true;
						if (doesTableExistInMoreThanOneLevel(levelHash)){
							singleOutputFile = false;	
						}
						List<Integer> list = getLevelHashInReverseOrder(levelHash.keySet());

						this.getLog().debug("Sorted list : " + list);

						int tables = 0;
						int rows = 0;
						String tab = "";
						if (list.size() > 0){
							this.getLog().info("Ready to export:");
						}
						
			            DatabaseConfig config = connection.getConfig();
			            config.setProperty(DatabaseConfig.PROPERTY_RESULTSET_TABLE_FACTORY, new CachedResultSetTableFactory());
			            
						Hashtable<Integer, IDataSet> compositeHash = new Hashtable<Integer, IDataSet>();
						for (Integer integer : list) {
							this.getLog().debug("Table Level: " + integer);
							Hashtable<String, TableNode> tableNodeHash = levelHash.get(integer);
							tab = tab + "\t";
							
							List<IDataSet> queryDataSetList = new ArrayList<IDataSet>();
							for (TableNode tableNode : tableNodeHash.values()) {
								if (!tableNode.isDuplicate()){
									String exportStatus = "";
									if (checkIfContains(autoExportConfigToExecute.getExcludedTableList(), tableNode.getId().toUpperCase())) {
										exportStatus = "- EXCLUDED FROM EXPORT";
									} else {
										QueryDataSet qds = new QueryDataSet(connection);
	
										qds.addTable(tableNode.getId(), tableNode.getQuery(autoExportConfigToExecute.getUseQuotedValues()));
										queryDataSetList.add(qds);
										tables = tables + 1;
										rows = rows + tableNode.getNumberOfPKValues();
									}
									
									this.getLog().info(tab + tableNode.getId() + " (" + tableNode.getNumberOfPKValues() + (tableNode.getNumberOfPKValues() == 1?" row":" rows") + ")." + exportStatus);
								}
							}
							IDataSet[] dataSetsArray = (IDataSet[])queryDataSetList.toArray( new IDataSet[queryDataSetList.size()] );
							CompositeDataSet cds = new CompositeDataSet(dataSetsArray);
							if (singleOutputFile) {
								completeQueryDataSetList.add(cds);
							} else {
								compositeHash.put(integer, cds);
							}
						}
						
						String frag = "";
						if (!singleOutputFile){
							frag = "these " + compositeHash.size() + " files ";
							this.getLog().info(
									"Exporting to DataSet directory: " + this.getFileDirectory(autoExportConfigToExecute.getDataSetFullPath()) + " [" + compositeHash.size() + " level based files]...");
							
						} else {
							frag = "this file";
							IDataSet[] dataSetsArray = (IDataSet[])completeQueryDataSetList.toArray( new IDataSet[completeQueryDataSetList.size()] );
							CompositeDataSet cds = new CompositeDataSet(dataSetsArray);
							compositeHash.put(0, cds);
							this.getLog().info(
									"Exporting to DataSet path: " + autoExportConfigToExecute.getDataSetFullPath() + "...");
						}

						
						this.getLog().info("Do you want to continue to export " + rows + " rows in " + tables + " tables to " + frag + " (Y|N)?");
						Scanner sc = new Scanner(System.in);
						String inp = sc.next();
						if (inp.equalsIgnoreCase("y")) {
							if (!singleOutputFile){
								int size = compositeHash.size();
								this.getLog().info("Please use DBUnit to import/refresh these files in the following order:");
								for (int i = size-1; i >= 0; i--) {
									File file = createFileWithSuffix(autoExportConfigToExecute.getDataSetFullPath(), "-level-" + i );
									writeFile(autoExportConfigToExecute,
											compositeHash, i, file);
									this.getLog().info(file.getName());
								}
							} else {
								//single
								File file = createFile(autoExportConfigToExecute.getDataSetFullPath() );
								writeFile(autoExportConfigToExecute,
										compositeHash, 0, file);
							}
							this.getLog().info((singleOutputFile?"File":"Files") + " written...");
						} 
					} finally {
						connection.close();
					}
				} else {
					throw new MojoExecutionException("The entered Id: "
							+ this.idToExport
							+ " does not exist in the plugin configuration!");
				}
			} catch (Exception e) {
				throw new MojoExecutionException("Error executing export", e);
			} finally {
				this.getLog().info("Namaste!");
			}

		}

	}
	
	private boolean checkIfContains(ExcludedTableList list, String what){
		boolean boo = false;
		if (list != null){
			String[] stringArray = list.getParamArray();
			if (stringArray != null){
				for(int i = 0; i < stringArray.length; i++){
					if (stringArray[i].equals(what)){
						boo= true;
						break;
					}
				}
			}
		}
		return boo;
	}

	private void writeFile(AutoExportConfig autoExportConfigToExecute,
			Hashtable<Integer, IDataSet> compositeHash, int i, File file)
			throws FileNotFoundException, IOException, DataSetException {
		OutputStream out = new FileOutputStream(file);
		IDataSet cds = compositeHash.get(i);
		try {
			XmlDataSet.write(cds, out, this.encoding);
		}finally {
			 out.close();
		}
	}
	
	/**
	 * Checks to see if any tableNode exists in more than one level AND it is not a duplicate
	 * 
	 * @param levelHash
	 * @return true if exists in more than one level
	 */
	private boolean doesTableExistInMoreThanOneLevel(Hashtable<Integer, Hashtable<String, TableNode>> levelHash){
		boolean boo = false;
		ArrayList<String> acrossLevelsList = new ArrayList<String>();
		
		Set<Integer> keySet = levelHash.keySet();
		for (Integer integer : keySet) {
			Set<String> withinLevelList = new HashSet<String>();
			Hashtable<String, TableNode> hash = levelHash.get(integer);
			for (String id : hash.keySet()) {
				TableNode tn = hash.get(id);
				if (!tn.isDuplicate()){
					withinLevelList.add(id);
				}
			}
			for (String string : withinLevelList) {
				if (acrossLevelsList.contains(string)){
					boo = true;
					break;
				}
				acrossLevelsList.add(string);
			}
			
		}
		return boo;
		
		
	}
	protected Hashtable<Integer, Hashtable<String, TableNode>> processTables(IDatabaseConnection connection,
			String baseTable, String whereClause) throws Exception {

		//This structure holds a level aware hash of nodes. It is used to determine the order of xml data produced by this plugin
		Hashtable<Integer, Hashtable<String, TableNode>> levelAwareNodeHash = new Hashtable<Integer, Hashtable<String, TableNode>>();
		
		TableNode rootTableNode = new TableNode();
		rootTableNode.setId(baseTable);
		rootTableNode.setBaseTable(true);
		rootTableNode.setWhereClause(whereClause);
		setBaseTableCount(rootTableNode, connection);

		putTableNodeAtLevelWithId(rootTableNode, levelAwareNodeHash);
		populateNode(connection, rootTableNode, levelAwareNodeHash);

		optimizeLevelAwareNodeHash(levelAwareNodeHash);
		
		if (this.getLog().isDebugEnabled()) {
			this.getLog().debug("Dump of table nodes:");
			dumpNode(rootTableNode);
		}

		return levelAwareNodeHash;
	}


	private void setBaseTableCount(TableNode node, IDatabaseConnection connection) {
		Connection sqlConnection = null;
		String sql = "";
		try {
			sqlConnection = connection.getConnection();
			sql = "SELECT count(*) count FROM " + node.getId()
					+ " "
					+ (!StringUtils.isEmpty(node.getWhereClause())?node.getWhereClause():"");
			this.getLog().debug("Base table SQL: " + sql);
			Statement stmt = sqlConnection.createStatement();
			ResultSet rs = stmt.executeQuery(sql);
			while (rs.next()) {
				node.setBaseTableCount(rs.getInt("count"));
			}
		} catch (Exception e) {
			throw new RuntimeException("Error executing " + sql, e);
		}
	}
	
	private void populateNode(IDatabaseConnection connection,
			TableNode tableNode, Hashtable<Integer, Hashtable<String, TableNode>> levelAwareNodeHash)
			throws Exception {
		Connection conn = connection.getConnection();
		String schema = connection.getSchema();
		DatabaseMetaData metaData = conn.getMetaData();

		QualifiedTableName qualifiedTableName = new QualifiedTableName(
				tableNode.getId(), schema);
		schema = qualifiedTableName.getSchema();
		String qTableName = qualifiedTableName.getTable();
		this.getLog().debug("Processing " + qTableName );
		//schema = schema.toLowerCase();
		ResultSet rs = metaData.getImportedKeys(null, schema, qTableName);

		while (rs.next()) {
			
			String fkName = rs.getString(12);
			String pkName = rs.getString(13);
			Short keySeq = rs.getShort(9);
			String fkColumn = rs.getString(8);
			String pkColumn = rs.getString(4);
			String pkTable = rs.getString(3);
			this.getLog().debug("ParentTable: " + qTableName + ", FKName: " + fkName + ", FKColumn: " + fkColumn + ", PKName: " + pkName + ", PKColumn: " + pkColumn + ", KeySeq: " + keySeq + ", childTable:" + pkTable) ;

			if (keySeq > 1) {
						this.getLog().info("Table "
								+ qTableName
								+ " has a composite key for Foreign Key "
								+ fkName
								+ ". This plugin cannot process composite keys. Please create dataset for "
								+ pkTable + " manually. Skipping...");
				continue;
			}

			// Get distinct values
			ArrayList<String> pkValueList = getPKValueListForChild(connection,
					fkColumn, tableNode);

			if (!CollectionUtils.isEmpty(pkValueList)) {
				// There are values in the FK column of the current table (that is, they are not all null)
				// Make a new node
				TableNode childNode = new TableNode();
				childNode.setId(pkTable);
				childNode.setPkColumn(pkColumn);
				childNode.setPkValueList(pkValueList);
				childNode.setLevel(tableNode.getLevel() + 1);
				childNode.setIncomingForeignKey(fkColumn);
				
				processLevelAwareNodeHash(childNode, levelAwareNodeHash);
				
				if (!childNode.isEmpty()) {			
					//Child has some PKs
					populateNode(connection, childNode, levelAwareNodeHash);
					//This is used to dump nodes for logging
					tableNode.addNonRepeatingDependentTableNode(childNode); 
			    } 
			}
		}
	}
	

	
	/**
	 * This method looks in <code>levelAwareNodeHash</code> for all nodes that are at the same level or higher
	 * than the current passed in node and checks to see if any node in that set contains the PKs that are on the current node.
	 * For those PKs that are *not* found, it adds a node (at the same level as the passed in node) with that PK added to it.
	 * For the PKs that are found, it does nothing.
	 * 
	 * The rationale is that if a PK is present at a higher level, it will be inserted BEFORE the same PK at the lower level is
	 * inserted, therefore, it is redundant.
	 * 
	 * For example, if node T1(10) is inserted at level 3 and then node T1(10) needs to be inserted at level 2, this method will not add that node 
	 * at level 2 because the row T1(10) at level 3 will be inserted BEFORE the row T1(10) at level 2 is inserted when the dataset is processed.
	 * 
	 * Also, if node T1(10) exists at level 2 and then a node T1(10) needs to be inserted at level 3, it WILL be inserted via this method.
	 * Note, that in the above scenario, the <code>optimizeLevelAwareNodeHash</code> method will mark the node T1(10) at level 2 as a
	 * duplicate, thereby preventing redundant dataset insertions and multiple file generation (which would happen if the same (non-duplicate)node appears
	 * in multiple levels).
	 * 
	 * @see optimizeLevelAwareNodeHash
	 * @param node
	 * @param alreadyProcessedNodeHash
	 * @return
	 */
	private void processLevelAwareNodeHash(TableNode node,  Hashtable<Integer, Hashtable<String, TableNode>> levelAwareNodeHash){

		ArrayList<String> currentPKList = (ArrayList<String>)node.getPkValueList();
		
		int highestLevel = getHighestLevelRecordedForNode(node, levelAwareNodeHash);
		
		if (currentPKList != null){
			for (String string : currentPKList) {
				boolean found = false;
				//Go from current level to highest level to see if the currenPK exists in previously recorded nodes...
				for (int i = node.getLevel(); i <= highestLevel; i++){ 
					TableNode compositeNode =  getTableNodeAtLevelWithId(i, node.getId(), levelAwareNodeHash);
					if (compositeNode != null){
						if (compositeNode.getPkValueList().contains(string)){ 
							found = true;
							break;
						} 
					}
				}
				if (!found){
					//One PK does not exist. Record the node and exit
					
					//First check to see if a node already exists on the hash
					//If it does, then add to it, else add a new node
					TableNode alreadyPresentNode = getTableNodeAtLevelWithId(node.getLevel(), node.getId(), levelAwareNodeHash);
					if (alreadyPresentNode != null){
						alreadyPresentNode.addNonRepeatingPKValue(string);
						putTableNodeAtLevelWithId(alreadyPresentNode, levelAwareNodeHash);
					} else {
						putTableNodeAtLevelWithId(node, levelAwareNodeHash);
					}
				}
			}
		}
	}
	
	
	/**
	 * The levelAwareNodeHash that is populated in the method <code>processLevelAwareNodeHash</code> can potentially have 
	 * duplicate nodes, that is nodes that have different levels but exactly the same id and pk values.
	 * This can happen because of the fact that the order in which a FK is processed and over which we have no control 
	 * (determined by the jdbc driver and dbUnit)
	 * In the processLevelAwareNodeHash method, let us assume the following scenario:
	 * A -> B
	 * A -> C -> B
	 * D -> B
	 * 
	 * The A -> C -> B pass will not be aware of node B that was created on the A -> B pass because it always looks at it's level and higher.
	 * Note that the A -> B pass occurred earlier in the run. 
	 * Note that the D -> B pass, WILL be aware of B because it already exists and at a higher level therefore the B node in the D -> B pass will be skipped 
	 * as designed and will not be present on the hash.
	 * 
	 * Therefore the purpose of this method is the go through the hash and mark as duplicate (except the first occurance) those nodes that have 
	 * the same PKs, starting from the highest level, down to the lowest level. In this example, the B node (generated by the A -> B pass) will be marked as
	 * duplicate
	 * 
	 * @param levelAwareNodeHash
	 * @see processLevelAwareNodeHash
	 */
	private void optimizeLevelAwareNodeHash(Hashtable<Integer, Hashtable<String, TableNode>> levelAwareNodeHash) {
		//First create a list of TableNodes
		//Note that this list can have repeat nodes, same ids different levels etc.
		ArrayList<TableNode> tableNodeList = new ArrayList<TableNode>();
		Set<Integer> keySet = levelAwareNodeHash.keySet();
		if (keySet != null){
			for (Integer integer : keySet) {
				Hashtable<String, TableNode> hash = levelAwareNodeHash.get(integer);
				Set<String> tNameSet = hash.keySet();
				if (tNameSet != null){
					for (String string : tNameSet) {
						TableNode tn = hash.get(string);
						tableNodeList.add(tn);
					}
				}
			}
		}
		
		//Look for each tn in reverse order, keeping only one occurance
		List<Integer> list = getLevelHashInReverseOrder(levelAwareNodeHash.keySet());
		for (TableNode node : tableNodeList) {
			boolean boo = false;
			for (Integer integer : list) {
				Hashtable<String, TableNode> hash = levelAwareNodeHash.get(integer);
				for (TableNode tNode : hash.values()) {
					if (tNode.exactlyEqual(node)){
						if (boo) {
							tNode.setDuplicate(true);
						}
						boo = true;
					}
				}
			}
		}

	}
	
	/**
	 * This method returns the highest level that is recorded for *this* node (using it's Id)
	 * @param node
	 * @param levelAwareNodeHash
	 * @return
	 */
	private Integer getHighestLevelRecordedForNode(TableNode node, Hashtable<Integer, Hashtable<String, TableNode>> levelAwareNodeHash){
		Integer highestLevel = 0;
		Set<Integer> levelSet = levelAwareNodeHash.keySet();
		if (levelSet != null){
			for (Integer integer : levelSet) {
				Hashtable<String, TableNode> hash = levelAwareNodeHash.get(integer);
				if (hash != null){
					TableNode currentNode = hash.get(node.getId());
					if (currentNode != null){
						if (currentNode.equals(node)){
							//ids are the same
							if (highestLevel < currentNode.getLevel()){
								highestLevel = currentNode.getLevel();
							}
						}
					}
				}
			}
		}
		return highestLevel;
	}


	private ArrayList<String> getPKValueListForChild(IDatabaseConnection connection,
			String fkColumn, TableNode node) {
		Connection sqlConnection = null;
		ArrayList<String> list = new ArrayList<String>();
		String sql = "";
		try {
			sqlConnection = connection.getConnection();
			sql = "SELECT DISTINCT " + fkColumn + " val FROM " + node.getId() + " ";

			
			String csList = "";
			if (this.isUseQuotedValues()){
				csList = node.getPKValuesAsCommaSeparatedListWithQuotes();
			} else {
				csList = node.getPKValuesAsCommaSeparatedList();
			}
			String s = node.getWhereClause();

			if ( (!StringUtils.isEmpty(node.getWhereClause())) ){
				
				s = s.trim().toUpperCase();
				if (s.startsWith("WHERE")){
					if (!CollectionUtils.isEmpty(node.getPkValueList())){
						s = " " + node.getWhereClause() + " AND " + node.getPkColumn() + " IN (" + csList + ")" ;
					}
				} else {
					this.getLog().warn("Incorrect WHERE clause specified. Ignoring " + node.getWhereClause());
				}
			} else {
				if (!CollectionUtils.isEmpty(node.getPkValueList())){
					s = " WHERE " + node.getPkColumn() + " IN (" + csList + ")" ;
				}
			}
			if  (!StringUtils.isEmpty(s)) {
				sql = sql + s;
			}
			
			this.getLog().debug("SQL: " + sql);
			Statement stmt = sqlConnection.createStatement();
			ResultSet rs = stmt.executeQuery(sql);
			while (rs.next()) {
				String v = rs.getString("val");
				if (!StringUtils.isEmpty(v)){
					list.add(v);
				}
			}

		} catch (Exception e) {
			throw new RuntimeException("Error executing " + sql, e);
		}
		return list;
	}


	private static List<Integer> getLevelHashInReverseOrder(Set<Integer> set) {
		ArrayList<Integer> list = new ArrayList<Integer>();
		for (Iterator<Integer> iterator = set.iterator(); iterator.hasNext();) {
			Integer integer = (Integer) iterator.next();
			list.add(integer);
		}
		Collections.sort(list);
		Collections.reverse(list);

		return list;
	}

	
	private TableNode getTableNodeAtLevelWithId(Integer level, String id, Hashtable<Integer, Hashtable<String, TableNode>> hash){
		TableNode tn = null;
		if (hash != null){
			Hashtable<String, TableNode> tableNodeHash = hash.get(level);
			if (tableNodeHash != null){
				tn = tableNodeHash.get(id);
			}
		}
		return tn;
	}
	
	private Hashtable<Integer, Hashtable<String, TableNode>> putTableNodeAtLevelWithId(TableNode node, Hashtable<Integer, Hashtable<String, TableNode>> hash){

		if (hash != null){
			Hashtable<String, TableNode> tableNodeHash = hash.get(node.getLevel());
			if (tableNodeHash == null){
				tableNodeHash = new Hashtable<String, TableNode>();
			}
			tableNodeHash.put(node.getId(), node);
			hash.put(node.getLevel(), tableNodeHash);
		}
		return hash;
	}	

	protected String listAutoExportConfigs(Hashtable<String, AutoExportConfig> hashtable,
			boolean prompt) throws MojoExecutionException { 
		Set<String> set = hashtable.keySet();
		ArrayList<String> list = new ArrayList<String>();

		for (Iterator<String> iterator = set.iterator(); iterator.hasNext();) {
			list.add((String) iterator.next());
		}

		Collections.sort(list);
		Hashtable<Integer, String> tempHash = new Hashtable<Integer, String>();
		int count = 1;
		for (Iterator<String> iterator = list.iterator(); iterator.hasNext();) {
			String id = (String) iterator.next();
			this.getLog().info(count + ". " + id);
			tempHash.put(count, id);
			count++;
		}
		String ret = null;
		if (prompt) {
			ret = promptForId(tempHash);
		}
		return ret;
	}

	private String promptForId(Hashtable<Integer, String> tempHash)
			throws MojoExecutionException {
		this
				.getLog()
				.info(
						"Enter the number next to the Id that you want to export, 0 to quit");
		Scanner sc = new Scanner(System.in);
		String inp = sc.next();
		int i = 0;

		try {
			i = Integer.parseInt(inp);
		} catch (Exception e) {
			throw new MojoExecutionException("Non-integer entered!");
		}
		if (i == 0) {
			return null;
		}
		return tempHash.get(i);
	}
	
	
	/**
	 * This method uses the TableNode dependentTables variable.
	 * The TableNode.dependentTables variables is not used anywhere else for processing.
	 * @param node
	 */
	private void dumpNode(TableNode node){
		String tab = "\t";
		String ftab = "";
		for (int i = 0; i < node.getLevel(); i++) {
			ftab = ftab + tab;
		}

		this.getLog().debug(ftab + "->" + node.getId() + ", IncomingFK: " + node.getIncomingForeignKey() + ", PK Values: [" + node.getPKValuesAsCommaSeparatedList() + "]" );
		List<TableNode> list = node.getDependsOnList();
		if (list != null){
			for (TableNode tableNode : list) {
				dumpNode(tableNode);
			}
		}
	}

	protected Hashtable<String, AutoExportConfig> checkCorrectnessOfAutoExportConfigAndBuildHash()
			throws MojoExecutionException {

		Hashtable<String, AutoExportConfig> hashtable = new Hashtable<String, AutoExportConfig>();

		AutoExportConfig[] aecArray = readAutoExportConfig(this.configFile);
		if (aecArray != null){
			for(int i = 0; i < aecArray.length; i++){
				AutoExportConfig autoExportConfig = aecArray[i];
				// Check Ids exist in all configs
				if (!StringUtils.isEmpty(autoExportConfig.getId())) {
					AutoExportConfig currentExportConfig = hashtable
							.get(autoExportConfig.getId());
					if (currentExportConfig != null) {
						throw new MojoExecutionException(
								"An autoExportConfig with Id: "
										+ autoExportConfig.getId()
										+ " is a duplicate. Ensure that all Ids are unique.");
					} else {
						hashtable.put(autoExportConfig.getId(), autoExportConfig);
					}
				} else {
					throw new MojoExecutionException(
							"An autoExportConfig configuration does not have it's Id specified.");
				}

				// Check dataSetPath exists
				if (StringUtils.isEmpty(autoExportConfig.getDataSetFullPath())) {
					throw new MojoExecutionException(
							"AutoExportConfig with id of "
									+ autoExportConfig.getId()
									+ " does not have a DataSetFullPath defined. Specify a DataSetFullpath for this autoExportConfig.");
				}

				if (StringUtils.isEmpty(autoExportConfig.getBaseTable())) {
					throw new MojoExecutionException("AutoExportConfig with id of "
							+ autoExportConfig.getId()
							+ " does not specify a base table.");
				}
			}
		}

		return hashtable;
	}


	public boolean isUseQuotedValues() {
		return useQuotedValues;
	}

	public void setUseQuotedValues(boolean useQuotedValues) {
		this.useQuotedValues = useQuotedValues;
	}

}
