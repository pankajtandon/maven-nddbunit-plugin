package com.nayidisha.plugins.nddbunit;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.StringUtils;
import org.dbunit.ant.Export;
import org.dbunit.ant.Query;
import org.dbunit.ant.Table;
import org.dbunit.database.IDatabaseConnection;

import com.nayidisha.plugins.nddbunit.schema.ExportConfigDocument;
import com.nayidisha.plugins.nddbunit.schema.Name;
import com.nayidisha.plugins.nddbunit.schema.QueryDocument;

/**
 * Execute specified DbUnit Export
 * 
 * @goal export
 * 
 */
public class ExportMojo extends AbstractDBUnitMojo {

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (skip) {
			this.getLog().info("Skip export execution");
			return;
		}
		super.execute(); 

		Hashtable<String, ExportConfigDocument.ExportConfig> hash = checkCorrectnessOfExportConfigAndBuildHash();
		if (StringUtils.isEmpty(idToExport)) {
			idToExport = this.listExportConfigs(hash, true);
		}
		if (!StringUtils.isEmpty(idToExport)) {
			ExportConfigDocument.ExportConfig exportConfigToExecute = hash.get(idToExport);

			try {
				if (exportConfigToExecute != null) {
					File file = createFile(exportConfigToExecute
							.getDataSetFullPath());
					this.getLog().info(
							"Exporting to DataSetPath: "
									+ exportConfigToExecute
											.getDataSetFullPath()
									+ " using URL: " + url + "...");

					IDatabaseConnection connection = createConnection();
					try {
						Export export = new Export();
						export.setOrdered(false);
						if (exportConfigToExecute.getQueryList() != null) {
							QueryDocument.Query[] querryArray = exportConfigToExecute.getQueryList().getQueryArray();
							
							for (int j = 0; j < querryArray.length; j++) {
								Query query = new Query();
								query.setName(querryArray[j].getName());
								query.setSql(querryArray[j].getSql());
								export.addQuery(query);
							}
						}

						if (exportConfigToExecute.getTableList() != null) {
							Name[] nameArray = exportConfigToExecute.getTableList().getTableArray();
							for (int j = 0; j < nameArray.length; j++) {
								Table table = new Table();
								table.setName(nameArray[j].getName());
								export.addTable(table);
							}
						}
						export.setDest(file);
						export.setDoctype(this.doctype);
						String format = (this.format);
						export.setFormat(format);
						export.setEncoding(this.encoding);

						export.execute(connection);

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
			}

		}

	}
	
	protected String listExportConfigs(Hashtable<String, ExportConfigDocument.ExportConfig> hashtable,
			boolean prompt) throws MojoExecutionException {
		Set set = hashtable.keySet();
		ArrayList<String> list = new ArrayList<String>();

		for (Iterator iterator = set.iterator(); iterator.hasNext();) {
			list.add((String) iterator.next());
		}

		Collections.sort(list);
		Hashtable<Integer, String> tempHash = new Hashtable<Integer, String>();
		int count = 1;
		for (Iterator iterator = list.iterator(); iterator.hasNext();) {
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


	protected Hashtable<String, ExportConfigDocument.ExportConfig> checkCorrectnessOfExportConfigAndBuildHash()
			throws MojoExecutionException {
		Hashtable<String, ExportConfigDocument.ExportConfig> hashtable = new Hashtable<String, ExportConfigDocument.ExportConfig>();

		ExportConfigDocument.ExportConfig[] ecArray = readExportConfig(this.configFile);
		if (ecArray != null) {
			for(int i =0; i < ecArray.length; i++){
				ExportConfigDocument.ExportConfig ec = ecArray[i];
				// Check Ids exist in all configs
				if (!StringUtils.isEmpty(ec.getId())) {
					ExportConfigDocument.ExportConfig currentExportConfig = hashtable.get(ec.getId());
					if (currentExportConfig != null) {
						throw new MojoExecutionException(
								"An exportConfig with Id: "
										+ ec.getId()
										+ " is a duplicate. Ensure that all Ids are unique.");
					} else {
						hashtable.put(ec.getId(), ec);
					}
				} else {
					throw new MojoExecutionException(
							"An exportConfig configuration does not have it's Id specified.");
				}

				// Check dataSetPath exists
				if (StringUtils.isEmpty(ec.getDataSetFullPath())) {
					throw new MojoExecutionException(
							"ExportConfig with id of "
									+ ec.getId()
									+ " does not have a DataSetFullPath defined. Specify a DataSetFullpath for this exportConfig.");
				}
				QueryDocument.Query[] querryArray = null;
				if (ec.getQueryList() != null){
					querryArray = ec.getQueryList().getQueryArray();
					// Check query is good
					if (querryArray != null) {
						for (int j = 0; j < querryArray.length; j++) {
							QueryDocument.Query query = querryArray[j];
							if (StringUtils.isEmpty(query.getName())
									|| StringUtils.isEmpty(query.getSql())) { 
								throw new MojoExecutionException(
										"You must specify BOTH, a query name AND a SQL for the query specified in the exportConfig with Id: "
												+ ec.getId());
							}
						}
					}
				}
				Name[] nameArray = null;
				if (ec.getTableList() != null){
					nameArray = ec.getTableList().getTableArray();
					// Check table is good
					if (nameArray != null) {
						for (int j = 0; j < nameArray.length; j++) {
							Name table = nameArray[j];
							if (StringUtils.isEmpty(table.getName())) {
								throw new MojoExecutionException(
										"You must specify a table name in the table specified in the exportConfig with Id: "
												+ ec.getId());
							}
						}
					}
				}

				// Check one of query or table is specified
				if ((querryArray != null)  && (nameArray != null)) {
					throw new MojoExecutionException(
							"ExportConfig with id of "
									+ ec.getId()
									+ " must specify AT LEAST ONE of a valid Query or Table.");
				}
			}
		}

		return hashtable;
	}

}
