package com.suncht.wordread.parser.wordx;

import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;

import com.google.common.base.Preconditions;
import com.suncht.wordread.model.TTCPr;
import com.suncht.wordread.model.TTCPr.TTCPrEnum;
import com.suncht.wordread.model.WordTable;
import com.suncht.wordread.model.WordTableCellContents;
import com.suncht.wordread.parser.ISingleWordTableParser;
import com.suncht.wordread.parser.WordTableTransferContext;
import com.suncht.wordread.parser.mapping.WordTableMemoryMapping;

/**
 * 
 * @author changtan.sun
 *
 */

/**
* 解析Docx中一张复杂表格内容
* Docx不仅有列合并，而且有行合并，没有列宽
* <p>标题: SingleWordXTableParser</p>  
* <p>描述: </p>  
* @author changtan.sun  
* @date 2018年4月27日
 */
public class SingleWordXTableParser implements ISingleWordTableParser {
	private XWPFTable xwpfTable;
	//	private WordTable table;

	private WordTableMemoryMapping _tableMemoryMapping;
	private WordTableTransferContext context;

	public SingleWordXTableParser(XWPFTable xwpfTable, WordTableTransferContext context) {
		this.xwpfTable = xwpfTable;
		this.context = context;
	}

	//	public WordTable getTable() {
	//		return table;
	//	}

	/**
	 * 解析Docx的表格，将表格相关数据映射到表格映射对象中， 用于后面的操作
	 * @return
	 */
	public WordTable parse() {
		List<XWPFTableRow> rows;
		List<XWPFTableCell> cells;

		rows = xwpfTable.getRows();
		int realMaxRowCount = rows.size();
		//		table.setRealMaxRowCount(rows.size());

		//计算最大列数
		int realMaxColumnCount = 0;
		for (XWPFTableRow row : rows) {
			//获取行对应的单元格  
			cells = row.getTableCells();
			int _columnCountOnRow = 0;
			for (XWPFTableCell cell : cells) {
				CTTcPr tt = cell.getCTTc().getTcPr();
				if(tt.getGridSpan()!=null) {
					_columnCountOnRow += tt.getGridSpan().getVal().intValue();
				} else {
					_columnCountOnRow += 1;
				}
			}
			
			if (_columnCountOnRow > realMaxColumnCount) {
				realMaxColumnCount = _columnCountOnRow;
			}
		}

		//table.setRealMaxColumnCount(columnCount);

		_tableMemoryMapping = new WordTableMemoryMapping(realMaxRowCount, realMaxColumnCount);
		_tableMemoryMapping.setVisitor(context.getVisitor());
		for (int i = 0; i < realMaxRowCount; i++) {
			parseRow(rows.get(i), i);
		}

		//printTableMemoryMap();

		//		wordTableMap = new WordTableMap();
		//		wordTableMap.setTableMemoryMap(_tableMemoryMap);
		return context.transfer(_tableMemoryMapping);
	}

	public void dispose() {
		_tableMemoryMapping = null;
		xwpfTable = null;
	}

	//	/**
	//	 * 打印表格映射
	//	 */
	//	private void printTableMemoryMap() {
	//		int r = 1;
	//		for (TTCPr[] columns : _tableMemoryMapping) {
	//			int c = 1;
	//			for (TTCPr column : columns) {
	//				System.out.println(r + ":" + c + "===>" + column.getType() + " ==== " + column.getText());
	//				c++;
	//			}
	//
	//			r++;
	//		}
	//	}

	/**
	 * 解析word中表格行
	 * @param row
	 * @param realRowIndex
	 */
	private void parseRow(XWPFTableRow row, int realRowIndex) {
		List<XWPFTableCell> cells = row.getTableCells();
		int numCells = cells.size();

		int logicColumnIndex = 0;
		int logicRowIndex = realRowIndex; //逻辑行号与实际行号一样
		for (int realColumnIndex = 0; realColumnIndex < numCells; realColumnIndex++) {
			XWPFTableCell cell = row.getCell(realColumnIndex);
			//skipColumn是否跳过多个单元格, 当列合并时候
			int skipColumn = parseCell(cell, realRowIndex, realColumnIndex, logicRowIndex, logicColumnIndex);
			logicColumnIndex = logicColumnIndex + skipColumn + 1;
		}
	}

	private int parseCell(XWPFTableCell cell, int realRowIndex, int realColumnIndex, int logicRowIndex,  int logicColumnIndex) {
		int skipColumn = 0;
//		if (_tableMemoryMapping.getTTCPr(realRowIndex, realColumnIndex) != null) {
//			return skipColumn;
//		}

		CTTcPr tt = cell.getCTTc().getTcPr();
		//-------行合并--------
		if (tt.getVMerge() != null) {
			if (tt.getVMerge().getVal() != null && "restart".equals(tt.getVMerge().getVal().toString())) { //行合并的第一行单元格(行合并的开始单元格)
				TTCPr ttc = new TTCPr();
				ttc.setType(TTCPrEnum.VM_S);
				ttc.setRealRowIndex(realRowIndex);
				ttc.setRealColumnIndex(realColumnIndex);
				ttc.setLogicRowIndex(logicRowIndex);
				ttc.setLogicColumnIndex(logicColumnIndex);
				ttc.setWidth(tt.getTcW().getW());
				ttc.setRoot(null);
				//ttc.setText(cell.getText());
				ttc.setContent(WordTableCellContents.getCellContent(cell));

				_tableMemoryMapping.setTTCPr(ttc, logicRowIndex, logicColumnIndex);
			} else { //行合并的其他行单元格（被合并的单元格）
				int _start = logicRowIndex, _end = 0;
				TTCPr root = null;
				for (int i = logicRowIndex - 1; i >= 0; i--) {
					TTCPr ttcpr = _tableMemoryMapping.getTTCPr(i, logicRowIndex);
					if (ttcpr != null && (ttcpr.getType() == TTCPrEnum.VM_S || ttcpr.getType() == TTCPrEnum.HVM_S)) {
						_end = i;
						root = ttcpr;
						break;
					} else if(ttcpr != null && ttcpr.getRoot()!=null) {
						_end = i;
						root = ttcpr.getRoot();
						break;
					}
				}

				Preconditions.checkNotNull(root, "父单元格不能为空");
				
				TTCPr ttc = new TTCPr();
				ttc.setType(TTCPrEnum.VM);
				ttc.setRealRowIndex(realRowIndex);
				ttc.setRealColumnIndex(realColumnIndex);
				ttc.setLogicRowIndex(logicRowIndex);
				ttc.setLogicColumnIndex(logicColumnIndex);
				ttc.setWidth(tt.getTcW().getW());
				ttc.setRoot(root);
				root.setRowSpan(_start - _end + 1);
				_tableMemoryMapping.setTTCPr(ttc, logicRowIndex, logicColumnIndex);
			}
		} else { //没有进行行合并的单元格
			TTCPr currentCell = _tableMemoryMapping.getTTCPr(logicRowIndex, logicColumnIndex);
			if (currentCell != null && currentCell.getType() == TTCPrEnum.HM) { //被列合并的单元格

			} else {
				currentCell = new TTCPr();
				currentCell.setType(TTCPrEnum.NONE);
				currentCell.setRealRowIndex(realRowIndex);
				currentCell.setRealColumnIndex(realColumnIndex);
				currentCell.setLogicRowIndex(logicRowIndex);
				currentCell.setLogicColumnIndex(logicColumnIndex);
				currentCell.setWidth(tt.getTcW().getW());
				currentCell.setContent(WordTableCellContents.getCellContent(cell));
				currentCell.setRoot(null);
				//判断是否有父单元格
				if (logicRowIndex > 0) {
					TTCPr parent = _tableMemoryMapping.getTTCPr(logicRowIndex - 1, logicColumnIndex);
					if (parent.isDoneColSpan()) {
						//currentCell.setParent(parent);
						currentCell.setRoot(parent);
					}
				}

				_tableMemoryMapping.setTTCPr(currentCell, logicRowIndex, logicColumnIndex);
			}
		}

		//-------列合并-------
		if (tt.getGridSpan() != null) {
			int colSpan = tt.getGridSpan().getVal().intValue();
			TTCPr root = _tableMemoryMapping.getTTCPr(logicRowIndex, logicColumnIndex);
			root.setColSpan(colSpan);
			if (root.getType() == TTCPrEnum.VM_S) {
				root.setType(TTCPrEnum.HVM_S);
			} else {
				root.setType(TTCPrEnum.HM_S);
			}

			//给其他被列合并的单元格进行初始化
			for (int i = 1; i < colSpan; i++) {
				TTCPr cell_other = _tableMemoryMapping.getTTCPr(logicRowIndex, logicColumnIndex + i);
				if (cell_other == null){
					cell_other = new TTCPr();
					cell_other.setWidth(tt.getTcW().getW());
				}
				cell_other.setRealRowIndex(realRowIndex);
				cell_other.setRealColumnIndex(realColumnIndex);
				cell_other.setLogicRowIndex(logicRowIndex);
				cell_other.setLogicColumnIndex(realColumnIndex + i);
				cell_other.setType(TTCPrEnum.HM);
				cell_other.setRoot(root);

				_tableMemoryMapping.setTTCPr(cell_other, logicRowIndex, realColumnIndex + i);
			}

			skipColumn = colSpan - 1;
		}

		return skipColumn;
	}
}
