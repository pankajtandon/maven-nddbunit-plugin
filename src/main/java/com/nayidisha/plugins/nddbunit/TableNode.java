package com.nayidisha.plugins.nddbunit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;

import org.codehaus.plexus.util.StringUtils;

public class TableNode {
	
	
	/**
	 * Used to store the table name although, it is imperative that this name is unique in the set of tables being processed
	 */
	
	private String id; 
	
	/**
	 * Distance or level from the root table.
	 */
	private Integer level = 0; 
	
	/**
	 * Specifies if this is the base table of the autoExportConfig
	 */
	private boolean baseTable;

	/**
	 * The number of rows in the exported base table.
	 */
	private int baseTableCount;

	/**
	 * List of tables that this table references, hence, depends on.
	 * At this time, this variable is ONLY used for dumping out the tree as a logged output,
	 * NOT for processing in the levelAwareHash
	 */
	private List<TableNode> dependsOnList;
	
	
	/**
	 * This indicates if this node is a duplicate of another node in the final hash
	 */
	private boolean duplicate;


	/**
	 * FK that has lead to this PK
	 */
	private String incomingForeignKey;
	

	private String pkColumn;
	
	private List<String> pkValueList;


	public List<String> getPkValueList() {
		return pkValueList;
	}


	public void setPkValueList(List<String> pkValueList) {
		this.pkValueList = pkValueList;
	}


	/**
	 * Holds WHERE clause for this node
	 */
	private String whereClause;
	

	/**
	 * Constructor. 
	 */
	public TableNode(){
		this.dependsOnList = new ArrayList<TableNode>();
		this.pkValueList = new ArrayList<String>();
		
	}
	
	
	public boolean isEmpty(){
		return  (CollectionUtils.isEmpty(this.getPkValueList()));
	}

	public String getQuery(boolean quoted){
		String q = "SELECT * FROM " + this.getId() ;
			
		
		if (StringUtils.isEmpty(this.getWhereClause())) {
			if (!StringUtils.isEmpty(this.getPkColumn())   && (!StringUtils.isEmpty(this.getPKValuesAsCommaSeparatedListWithQuotes()))) {
				q = q + " WHERE " + this.getPkColumn() + " IN ( "  + (quoted?this.getPKValuesAsCommaSeparatedListWithQuotes():this.getPKValuesAsCommaSeparatedList()) + " )";
			}
		} else {
			q = q + " " + this.getWhereClause();
		}
		return q;
	}
	
	public void addDependentTableNode(TableNode tn){
		this.dependsOnList.add(tn);
	}
	
	public void addNonRepeatingDependentTableNode(TableNode node){
		if (this.getDependsOnList() != null) {
			boolean exists = false;
			for (TableNode tableNode : this.getDependsOnList()) {
				if (tableNode.equals(node)){
					exists = true;
					break;
				}
			}
			
			if (!exists){
				this.getDependsOnList().add(node);
			}
		}
	}
	
	
	public void addPKValue(String value){
		this.getPkValueList().add(value);
	}

	public void addNonRepeatingPKValue(String value){
		if (!this.getPkValueList().contains(value)){
			this.getPkValueList().add(value);
		}
	}
	public void addNonRepeatingPKValue(List<String> pkValueList){
		if (pkValueList != null){
			for (String string : pkValueList) {
				this.addNonRepeatingPKValue(string);
			}
		}
	}
	public String getPKValuesAsCommaSeparatedList(){
		String s = "";
		if (!CollectionUtils.isEmpty(this.getPkValueList())){
			List<String> list = this.getPkValueList();
			for (String string : list) {
				s = s + string + ", ";
			}
		}
		
		if (s.endsWith(", ")){
			s = s.substring(0, s.length() - 2);
		}
		return s;
	}
	
	public String getPKValuesAsCommaSeparatedListWithQuotes(){
		String s = "";
		if (!CollectionUtils.isEmpty(this.getPkValueList())){
			List<String> list = this.getPkValueList();
			for (String string : list) {
				s = s + "'" + string + "', ";
			}
		}
		
		if (s.endsWith(", ")){
			s = s.substring(0, s.length() - 2);
		}
		return s;
	}
	
	public int getNumberOfPKValues(){
		int i = 0;
		if (this.isBaseTable()){
			i = this.baseTableCount;
		} else {
			i = this.getPkValueList().size();
		}
		
		return i;
	}
	
	/**
	 * The ids match
	 * @param that
	 * @return
	 */
	public boolean equals(TableNode that){
		boolean boo = false;
		if (that instanceof TableNode) {
			if (this.getId().equals(that.getId())){
				boo = true;
			}
		}
		return boo;
	}
	

	/**
	 * The id AND PKValues match
	 * @param that
	 * @return
	 */
	public boolean exactlyEqual(TableNode that){
		boolean boo = false;
		if (that instanceof TableNode) {
			if (this.getId().equals(that.getId())){
				Collections.sort(this.getPkValueList());
				Collections.sort(that.getPkValueList());
				if (CollectionUtils.isEqualCollection(this.getPkValueList(), that.getPkValueList())) {
					boo = true;
				}
			}
		}
		return boo;
	}
	
	
	public String toString(){
		return this.getLevel() + "|" + this.getId() + "| FK Columns: " +  "| PK Column: " + this.getPkColumn() +  "| Dependends On: " + this.getDependsOnList() + "| PKValues: [" + this.getPKValuesAsCommaSeparatedList() + "]";
	}

	
	
	//--------------getters/setters------------------
	
	
	public String getIncomingForeignKey() {
		return incomingForeignKey;
	}


	public void setIncomingForeignKey(String incomingForeignKey) {
		this.incomingForeignKey = incomingForeignKey;
	}
	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}


	public Integer getLevel() {
		return level;
	}


	public void setLevel(Integer level) {
		this.level = level;
	}


	public List<TableNode> getDependsOnList() {
		return dependsOnList;
	}


	public void setDependsOnList(List<TableNode> dependsOnList) {
		this.dependsOnList = dependsOnList;
	}

	
	public String getWhereClause() {
		return whereClause;
	}



	public void setWhereClause(String whereClause) {
		this.whereClause = whereClause;
	}
	public boolean isBaseTable() {
		return baseTable;
	}


	public void setBaseTable(boolean baseTable) {
		this.baseTable = baseTable;
	}
	

	public int getBaseTableCount() {
		return baseTableCount;
	}


	public void setBaseTableCount(int baseTableCount) {
		this.baseTableCount = baseTableCount;
	}
	
	public String getPkColumn() {
		return pkColumn;
	}


	public void setPkColumn(String pkColumn) {
		this.pkColumn = pkColumn;
	}

	public boolean isDuplicate() {
		return duplicate;
	}

	public void setDuplicate(boolean duplicate) {
		this.duplicate = duplicate;
	}
}
