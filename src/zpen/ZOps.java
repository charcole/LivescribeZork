package zpen;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import java.util.Stack;
import java.util.Vector;
import java.lang.Error;

import com.livescribe.penlet.Logger;

public class ZOps {
	static final int SrcImmediate=0;
	static final int SrcVariable=1;
	
	static final int Form0OP=0;
	static final int Form1OP=1;
	static final int Form2OP=2;
	static final int FormVAR=3;
	
	public static final int zeroOpStoreInstructionsV3[]={};
	public static final int zeroOpStoreInstructionsV4[]={0x05,0x06};
	public static final int zeroOpStoreInstructionsV5[]={0x09};
	public static final int oneOpStoreInstructionsV34[]={0x01,0x02,0x03,0x04,0x08,0x0E,0x0F};
	public static final int oneOpStoreInstructionsV5[]={0x01,0x02,0x03,0x04,0x08,0x0E};
	public static final int twoOpStoreInstructionsV345[]={0x08,0x09,0x0F,0x10,0x11,0x12,0x13,0x14,0x15,0x16,0x17,0x18,0x19};
	public static final int varOpStoreInstructionsV34[]={0x00,0x07,0x0C,0x16,0x17,0x18};
	public static final int varOpStoreInstructionsV5[]={0x00,0x04,0x07,0x0C,0x16,0x17,0x18};
	
	public static final int zeroOpBranchInstructionsV3[]={0x05,0x06,0x0D,0x0F};
	public static final int zeroOpBranchInstructionsV45[]={0x0D,0x0F};
	public static final int oneOpBranchInstructionsV345[]={0x00,0x01,0x02};
	public static final int twoOpBranchInstructionsV345[]={0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x0A};
	public static final int varOpBranchInstructionsV345[]={0x17,0x1F};
	
	public class ZOperand {
		int value;
		int src;
	}
	
	public class ZBranch {
		int offset;
		boolean negate;
		public ZBranch(int off, boolean neg)
		{
			offset=off;
			negate=neg;
		}
	}
	
	public class ZInstruction {
		int op;
		int form;
		int store;
		ZBranch branch;
		Vector operands;
	}
	
	public class ZCallStack {
		int returnAddr;
		int returnStore;
		int locals[];
		int depth;
	}
	
	public class ZObject {
		int addr;
		int propTable;
		int parent;
		int sibling;
		int child;
	}
	
	public class ZProperty {
		int addr;
		int size;
		boolean bDefault;
	}
	
	public class ZToken
	{
		String token;
		int offset;
	}
	
	public class ZDictEntry
	{
		ZDictEntry() {
			coded = new int[6];
			current=0;
			if (m_version<4)
				coded[2]|=0x80;
			else
				coded[4]|=0x80;
		}
		void addCharacter(int code) {
			code&=31;
			switch (current)
			{
				case 0: coded[0]|=code<<2; break;
				case 1: coded[0]|=code>>3; coded[1]|=(code<<5)&0xFF; break;
				case 2: coded[1]|=code; break;
				case 3: coded[2]|=code<<2; break;
				case 4: coded[2]|=code>>3; coded[3]|=(code<<5)&0xFF; break;
				case 5: coded[3]|=code; break;
				case 6: coded[4]|=code<<2; break;
				case 7: coded[4]|=code>>3; coded[5]|=(code<<5)&0xFF; break;
				case 8: coded[5]|=code; break;
			}
			current++;
		}
		int coded[];
		int current;
	}
	
	public char alphabetLookup[][]={
			{ 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z' },
			{ 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z' },
			{ ' ', '\n','0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.', ',', '!', '?', '_', '#', '\'','"', '/', '\\','-', ':', '(', ')' },
	};
	
	public ZInstruction m_ins;
	public int m_pc;
	public int m_globalVariables;
	public int m_abbrevTable;
	public int m_objectTable;
	public int m_dictionaryTable;
	public int m_memSize;
	public int m_version;
	public int m_packedMultiple;
	public int m_numDefaultProperties;
	public int m_objectEntrySize;
	public int m_staticMemory;
	public int m_crc;
	public byte memory[];
	public Random m_rand;
	public Stack m_stack;
	public Stack m_callStack;
	public String m_gameName;
	public Logger m_logger;
	
	public String m_output;
	public boolean m_bNeedInput;
	public boolean m_bNeedSaving;
	public boolean m_bNeedRestoring;
	public boolean m_bNeedToQuit;
	
	public ZOps(String gameName, Logger logger)
	{
		m_gameName=gameName;
		m_logger=logger;
		restartGame(m_gameName);
	}
	
	public int readBytePC()
	{
		return memory[m_pc++]&0xFF;
	}
	
	public int makeS16(int msb, int lsb)
	{
		int ret=(msb<<8)+lsb;
		if ((ret&0x8000)!=0)
		{
			ret+=-0x10000;
		}
		return ret;
	}
	
	public int makeU16(int msb, int lsb)
	{
		return (msb<<8)+lsb;
	}
	
	public int readS16PC()
	{
		int msb=readBytePC();
		int lsb=readBytePC();
		return makeS16(msb, lsb);
	}
	
	public boolean restartGame(String resourceName)
	{
		try
		{
			InputStream fis = getClass().getResourceAsStream(resourceName);
			m_memSize=fis.available();
			memory = new byte[m_memSize];
			fis.read(memory);
			fis.close();
		}
		catch (IOException ex)
		{
			m_output="Sorry couldn't open " + resourceName;
			memory = null;
			return false;
		}
		m_globalVariables=makeU16(memory[0xC]&0xFF, memory[0xD]&0xFF);
		m_abbrevTable=makeU16(memory[0x18]&0xFF, memory[0x19]&0xFF);
		m_objectTable=makeU16(memory[0xA]&0xFF, memory[0xB]&0xFF);
		m_dictionaryTable=makeU16(memory[0x8]&0xFF, memory[0x9]&0xFF);
		m_staticMemory=makeU16(memory[0xE]&0xFF, memory[0xF]&0xFF);
		m_crc=makeU16(memory[0x1C]&0xFF, memory[0x1D]&0xFF);
		m_pc=makeU16(memory[6]&0xFF, memory[7]&0xFF);
		m_version = memory[0];
		switch (m_version)
		{
		case 1:
		case 2:
		case 3:
			m_packedMultiple = 2;
			m_numDefaultProperties=31;
			m_objectEntrySize=9;
			break;
		case 4:
		case 5:
			m_packedMultiple = 4;
			m_numDefaultProperties=63;
			m_objectEntrySize=14;
			break;
		case 8:
			m_packedMultiple = 8;
			m_numDefaultProperties=63;
			m_objectEntrySize=14;
			break;
		}
		if (m_version<4)
		{
			memory[1]|=(1<<4); // status line not available
			memory[1]&=~(1<<5); // screen splitting available
			memory[1]&=~(1<<6); // variable pitch font
		}
		else
		{
			memory[1]&=~(1<<0); // colour available
			memory[1]&=~(1<<1); // pictures available
			memory[1]&=~(1<<2); // bold face available
			memory[1]&=~(1<<3); // italics available
			memory[1]&=~(1<<4); // fixed font available
			memory[1]&=~(1<<5); // sound effects available
			memory[1]&=~(1<<6); // timed keyboard available
			memory[0x1E]=6;
			memory[0x1F]='A';
			memory[0x20]=(byte)255; // infinite screen height
			memory[0x21]=(byte)80; // 80 character screen width
		}
		memory[0x10]|=(1<<0); // transcribing
		memory[0x10]|=(1<<1); // fixed font
		if (m_version>4)
		{
			memory[0x10]&=~(1<<3); // don't use pictures
			memory[0x10]&=~(1<<4); // don't use undo
			memory[0x10]&=~(1<<5); // don't use mouse
			memory[0x10]&=~(1<<6); // don't use colours
			memory[0x10]&=~(1<<7); // don't use sound
		}
		m_ins = new ZInstruction();
		m_ins.operands = new Vector();
		m_callStack = new Stack();
		m_stack = new Stack();
		m_rand = new Random();
		m_bNeedInput = false;
		m_bNeedSaving = false;
		m_bNeedRestoring = false;
		m_bNeedToQuit = false;
		m_output="";
		return true;
	}
	
	public boolean saveGame(DataOutputStream out)
	{
		m_bNeedInput=false;
		m_bNeedSaving=false;
		m_output="";
		try
		{
			if (out==null)
				throw new IOException("Data stream is null");
			out.writeUTF(m_gameName);
			out.writeInt(m_crc);
			out.writeInt(m_ins.store);
			out.writeBoolean(m_ins.branch.negate);
			out.writeInt(m_ins.branch.offset);
			out.write(memory, 0, m_staticMemory);
			out.writeInt(m_stack.size());
			for (int i=0; i<m_stack.size(); i++)
			{
				out.writeInt(((Integer)m_stack.elementAt(i)).intValue());
			}
			out.writeInt(m_callStack.size());
			for (int i=0; i<m_callStack.size(); i++)
			{
				ZCallStack cs=(ZCallStack)m_callStack.elementAt(i);
				out.writeInt(cs.returnAddr);
				out.writeInt(cs.returnStore);
				out.writeInt(cs.depth);
				out.writeInt(cs.locals.length);
				for (int k=0; k<cs.locals.length; k++)
				{
					out.writeInt(cs.locals[k]);
				}
			}
			out.writeInt(m_pc);
			if (m_version<4)
				doBranch(true, m_ins.branch);
			else
				setVariable(m_ins.store, 1);
			return true;
		}
		catch (IOException ex)
		{
			if (m_version<4)
				doBranch(false, m_ins.branch);
			else
				setVariable(m_ins.store, 0);
			return false;
		}
	}
	
	public void restoreGame(DataInputStream in) throws IOException
	{
		m_gameName=in.readUTF();
		if (!restartGame(m_gameName))
			throw new IOException("Couldn't reload "+m_gameName);
		if (m_crc!=in.readInt())
			throw new IOException("CRC mismatch");
		m_ins.store=in.readInt();
		boolean branchNegate=in.readBoolean();
		m_ins.branch=new ZBranch(in.readInt(),branchNegate);
		in.read(memory, 0, m_staticMemory);
		int stackSize=in.readInt();
		for (int i=0; i<stackSize; i++)
		{
			m_stack.push(new Integer(in.readInt()));
		}
		int callStackSize=in.readInt();
		for (int i=0; i<callStackSize; i++)
		{
			ZCallStack cs=new ZCallStack();
			cs.returnAddr=in.readInt();
			cs.returnStore=in.readInt();
			cs.depth=in.readInt();
			cs.locals=new int[in.readInt()];
			for (int k=0; k<cs.locals.length; k++)
			{
				cs.locals[k]=in.readInt();
			}
			m_callStack.push(cs);
		}
		m_pc=in.readInt();
		if (m_version<4)
			doBranch(true, m_ins.branch);
		else
			setVariable(m_ins.store, 2);
		m_output="[Restored game.]\n";
	}
	
	public void restoreFailed()
	{
		m_bNeedInput=false;
		m_bNeedRestoring=false;
		m_output="";
		if (m_version<4)
			doBranch(false, m_ins.branch);
		else
			setVariable(m_ins.store, 0);
	}
	
	public void assertObjectTreeGood()
	{
		boolean anyNotGood=false;
		ZObject zero=getObject(1);
		int numObjs=(zero.propTable-zero.addr)/m_objectEntrySize;
		for (int i=1; i<=numObjs; i++)
		{
			boolean good=false;
			ZObject obj=getObject(i);
			if (obj.parent!=0)
			{
				ZObject parent=getObject(obj.parent);
				if (parent.child==i)
				{
					good=true;
				}
				else
				{
					int sibId=parent.child;
					while (sibId!=0)
					{
						ZObject sib=getObject(sibId);
						if (sibId==i)
						{
							good=true;
							break;
						}
						sibId=sib.sibling;
					}
				}
				if (!good)
				{
					m_logger.debug("Object "+i+" has parent "+obj.parent+" but it doesn't own it");
					anyNotGood=true;
				}
			}
		}
		if (anyNotGood)
		{
			illegalInstruction();
		}
	}
	
	public ZObject getObject(int id)
	{
		ZObject ret = new ZObject();
		ret.addr=m_objectTable+2*m_numDefaultProperties+m_objectEntrySize*(id-1);
		ret.propTable=makeU16(memory[ret.addr+m_objectEntrySize-2]&0xFF, memory[ret.addr+m_objectEntrySize-1]&0xFF);
		if (m_version>3)
		{
			ret.parent=makeU16(memory[ret.addr+6]&0xFF, memory[ret.addr+7]&0xFF);
			ret.sibling=makeU16(memory[ret.addr+8]&0xFF, memory[ret.addr+9]&0xFF);
			ret.child=makeU16(memory[ret.addr+10]&0xFF, memory[ret.addr+11]&0xFF);
		}
		else
		{
			ret.parent=memory[ret.addr+4]&0xFF;
			ret.sibling=memory[ret.addr+5]&0xFF;
			ret.child=memory[ret.addr+6]&0xFF;
		}
		return ret;
	}
	
	public void setObjectParent(ZObject obj, int id)
	{
		obj.parent=id;
		if (m_version>3)
		{
			memory[obj.addr+6]=(byte)((obj.parent>>8)&0xFF);
			memory[obj.addr+7]=(byte)(obj.parent&0xFF);
		}
		else
		{
			memory[obj.addr+4]=(byte)obj.parent;
		}
	}
	
	public void setObjectSibling(ZObject obj, int id)
	{
		obj.sibling=id;
		if (m_version>3)
		{
			memory[obj.addr+8]=(byte)((obj.sibling>>8)&0xFF);
			memory[obj.addr+9]=(byte)(obj.sibling&0xFF);
		}
		else
		{
			memory[obj.addr+5]=(byte)obj.sibling;
		}
	}
	
	public void setObjectChild(ZObject obj, int id)
	{
		obj.child=id;
		if (m_version>3)
		{
			memory[obj.addr+10]=(byte)((obj.child>>8)&0xFF);
			memory[obj.addr+11]=(byte)(obj.child&0xFF);
		}
		else
		{
			memory[obj.addr+6]=(byte)obj.child;
		}
	}
	
	public ZProperty getProperty(ZObject obj, int id)
	{
		ZProperty ret = new ZProperty();
		int address=obj.propTable;
		int textLen=memory[address++]&0xFF;
		address+=textLen*2;
		while (memory[address]!=0)
		{
			int size, propId;
			int sizeId=memory[address++]&0xFF;
			if (m_version<4)
			{
				size=1+(sizeId>>5);
				propId=sizeId&31;
			}
			else
			{
				propId=sizeId&63;
				if ((sizeId&0x80)==0x80)
				{
					int sizeId2=memory[address++]&0xFF;
					size=sizeId2&63;
					if (size==0)
						size=64;
				}
				else
				{
					size=((sizeId&0x40)==0x40)?2:1;
				}
			}
			if (propId==id)
			{
				ret.addr=address;
				ret.size=size;
				ret.bDefault=false;
				return ret;
			}
			address+=size;
		}
		ret.addr=(m_objectTable+(id-1)*2)&0xFFFF;
		ret.size=2;
		ret.bDefault=true;
		return ret;
	}
	
	public void readOperand(int operandType)
	{
		if (operandType==3) //omitted
		{
			return;
		}
		ZOperand operand=new ZOperand();
		switch (operandType)
		{
		case 0: // long constant
			operand.value = readS16PC();
			operand.src = SrcImmediate;
			break;
		case 1: // small constant
			operand.value = readBytePC();
			operand.src = SrcImmediate;
			break;
		case 2: // variable
			operand.value = readVariable(readBytePC());
			operand.src = SrcVariable;
			break;
		}
		m_ins.operands.addElement(operand);
	}
	
	public void readShortForm(int opcode)
	{
		int operand=(opcode>>4)&3;
		int op=opcode&15;
		m_ins.op=op;
		if (operand==3)
			m_ins.form=Form0OP;
		else
			m_ins.form=Form1OP;
		readOperand(operand);
	}
	
	public void readLongForm(int opcode)
	{
		int op=opcode&31;
		m_ins.op=op;
		m_ins.form=Form2OP;
		readOperand(((opcode&(1<<6))!=0)?2:1);
		readOperand(((opcode&(1<<5))!=0)?2:1);
	}
	
	public void readVariableForm(int opcode)
	{
		int op=opcode&31;
		int operandTypes=readBytePC();
		m_ins.op=op;
		if ((opcode&0xF0)>=0xE0)
			m_ins.form=FormVAR;
		else
			m_ins.form=Form2OP;
		for (int i=3; i>=0; i--)
		{
			readOperand((operandTypes>>(2*i))&3);
		}
		if (m_ins.form==FormVAR && (op==0xC || op==0x1A)) // call_vs2 and call_vn2
		{
			operandTypes=readBytePC();
			for (int i=3; i>=0; i--)
			{
				readOperand((operandTypes>>(2*i))&3);
			}
		}
	}
	
	public int readStoreInstruction(int match[], int op)
	{
		for (int i=0; i<match.length; i++)
		{
			if (op==match[i])
			{
				return readBytePC();
			}
		}
		return -1;
	}
	
	public ZBranch readBranchInstruction(int match[], int op)
	{
		for (int i=0; i<match.length; i++)
		{
			if (op==match[i])
			{
				int branch1=readBytePC();
				if ((branch1&(1<<6))==0)
				{
					int branch2=readBytePC();
					int offset=((branch1&63)<<8)+branch2;
					if ((offset&(1<<13))!=0)
					{
						offset+=-(1<<14);
					}
					return new ZBranch(offset, (branch1&0x80)==0);
				}
				else
				{
					return new ZBranch(branch1&63, (branch1&0x80)==0);
				}
			}
		}
		return new ZBranch(0,false);
	}
	
	public void callRoutine(int address, int returnStore, boolean setOperands)
	{
		if (address==0)
		{
			if (returnStore>=0)
			{
				setVariable(returnStore, 0);
			}
		}
		else
		{
			int numLocals=memory[address++]%0xFF;
			ZCallStack cs=new ZCallStack();
			cs.returnAddr=m_pc;
			cs.returnStore=returnStore;
			cs.locals=new int[numLocals];
			for (int i=0; i<numLocals; i++)
			{
				if (m_version>4)
				{
					cs.locals[i]=0;
				}
				else
				{
					cs.locals[i]=makeS16(memory[address]&0xFF, memory[address+1]&0xFF);
					address+=2;
				}
			}
			if (setOperands)
			{
				for (int i=0; i<numLocals && i<m_ins.operands.size()-1; i++)
				{
					cs.locals[i]=((ZOperand)m_ins.operands.elementAt(i+1)).value;
				}
			}
			cs.depth=m_stack.size();
			m_pc=address;
			m_callStack.push(cs);
		}
	}
	
	public void returnRoutine(int value)
	{
		ZCallStack cs=(ZCallStack)m_callStack.pop();
		while (cs.depth<m_stack.size())
		{
			m_stack.pop();
		}
		if (cs.returnStore!=-1)
		{
			setVariable(cs.returnStore, value);
		}
		m_pc=cs.returnAddr;
	}
	
	public void doBranch(boolean cond, ZBranch branch)
	{
		if (branch.negate)
		{
			cond=!cond;
		}
		if (cond)
		{
			if (branch.offset==0)
				returnRoutine(0);
			else if (branch.offset==1)
				returnRoutine(1);
			else
				m_pc+=branch.offset-2;
		}
	}
	
	public int readVariable(int var)
	{
		if (var==0)
		{
			return ((Integer)m_stack.pop()).intValue();
		}
		if (var<16)
		{
			return ((ZCallStack)m_callStack.peek()).locals[var-1];
		}
		int off=2*(var-16);
		off+=m_globalVariables;
		return makeS16(memory[off]&0xFF, memory[off+1]&0xFF); 
	}
	
	public void setVariable(int var, int value)
	{
		value&=0xFFFF;
		if ((value&0x8000)!=0)
		{
			value+=-0x10000;
		}
		if (var==0)
		{
			m_stack.push(new Integer(value));
			return;
		}
		if (var<16)
		{
			((ZCallStack)m_callStack.peek()).locals[var-1]=value;
			return;
		}
		int off=2*(var-16);
		off+=m_globalVariables;
		memory[off+0]=(byte)((value&0xFF00)>>8);
		memory[off+1]=(byte)((value&0x00FF)>>0); 
	}
	
	public String getOutput()
	{
		return m_output;
	}
	
	public void print(String toPrint)
	{
		m_output+=toPrint;
	}
	
	public void print(char c)
	{
		print(String.valueOf(c));
	}
	
	public void print()
	{
		print("");
	}
	
	public void println(String toPrint)
	{
		print(toPrint + "\n");
	}
	
	public void println(char c)
	{
		println(String.valueOf(c));
	}
	
	public void println()
	{
		println("");
	}
	
	public void displayState()
	{
		println("Next PC:" + m_pc);
		println("Form:"+ m_ins.form + " Opcode:" + m_ins.op);
		println("Num operands:" + m_ins.operands.size());
		for (int i=0; i<m_ins.operands.size(); i++)
		{
			println("Value:" + ((ZOperand)m_ins.operands.elementAt(i)).value + " Src:" + ((ZOperand)m_ins.operands.elementAt(i)).src); 
		}
		println("Store:" + m_ins.store + " Branch:" + m_ins.branch.offset + (m_ins.branch.negate?" Negated":" Normal"));
	}
	
	public void dumpCurrentInstruction()
	{
		for (int i=0; i<m_ins.operands.size(); i++)
		{ 
			m_logger.debug("Arg:"+i+" Value:" + (((ZOperand)m_ins.operands.elementAt(i)).value&0xFFFF));
		}
	}
	
	public void haltInstruction()
	{
		println();
		println();
		println("Unimplemented instruction!");
		displayState();
		throw new Error("Unimpleded");
	}
	
	public void illegalInstruction()
	{
		println();
		println();
		println("Illegal Instruction!");
		displayState();
		throw new Error("Illegal");
	}
	
	public int printText(int address)
	{
		int pair1=0, pair2=0;
		int alphabet=0;
		int characters[]=new int[3];
		boolean abbrNext=false;
		int longNext=0;
		int longChar=0;
		int abbrChar=0;
		while ((pair1&0x80)==0)
		{
			pair1=memory[address++]&0xFF;
			pair2=memory[address++]&0xFF;
			characters[0]=(pair1&0x7C)>>2;
			characters[1]=((pair1&3)<<3) + ((pair2&0xE0)>>5);
			characters[2]=pair2&0x1F;
			for (int i=0; i<3; i++)
			{
				if (longNext>0)
				{
					longChar<<=5;
					longChar&=0x3FF;
					longChar|=characters[i];
					longNext--;
					if (longNext==0)
					{
						print((char)longChar);
					}
				}
				else if (!abbrNext)
				{
					if (characters[i]==6 && alphabet==2)
					{
						longNext=2;
					}
					else if (characters[i]>=6)
					{
						characters[i]-=6;
						print(alphabetLookup[alphabet][characters[i]]);
						alphabet=0;
					}
					else if (characters[i]==4)
					{
						alphabet=1;
					}
					else if (characters[i]==5)
					{
						alphabet=2;
					}
					else if (characters[i]==0)
					{
						print(" ");
					}
					else
					{
						abbrChar=characters[i];
						abbrNext=true;
					}
				}
				else
				{
					int idx=32*(abbrChar-1)+characters[i];
					int abbrevTable=m_abbrevTable+2*idx;
					int abbrevAddress=makeU16(memory[abbrevTable]&0xFF, memory[abbrevTable+1]&0xFF);
					printText(2*abbrevAddress);
					abbrNext=false;
				}
			}
		}
		return address;
	}
	
	public void removeObject(int childId)
	{
		ZObject child=getObject(childId);
		int parentId=child.parent;
		if (parentId!=0)
		{
			ZObject parent=getObject(parentId);
			if (parent.child==childId)
			{
				setObjectChild(parent, child.sibling);
			}
			else
			{
				int siblingId=parent.child;
				while (siblingId!=0)
				{
					ZObject sibling=getObject(siblingId);
					int nextSiblingId=sibling.sibling;
					if (nextSiblingId==childId)
					{
						setObjectSibling(sibling,child.sibling);
						break;
					}
					siblingId=nextSiblingId;
				}
				if (siblingId==0)
				{
					illegalInstruction();
				}
			}
			setObjectParent(child,0);
			setObjectSibling(child,0);
		}
	}
	
	public void addChild(int parentId, int childId)
	{
		ZObject child=getObject(childId);
		ZObject parent=getObject(parentId);
		setObjectSibling(child,parent.child);
		setObjectParent(child,parentId);
		setObjectChild(parent,childId);
	}
	
	public ZDictEntry encodeToken(String token)
	{
		ZDictEntry ret = new ZDictEntry();
		for (int t=0; t<token.length(); t++)
		{
			char curChar = token.charAt(t);
			int alphabet=-1;
			int code=-1;
			for (int a=0; a<alphabetLookup.length && alphabet==-1; a++)
			{
				for (int i=0; i<alphabetLookup[a].length; i++)
				{
					if (curChar == alphabetLookup[a][i])
					{
						alphabet=a;
						code=i;
						break;
					}
				}
			}
			if (alphabet==-1)
			{
				ret.addCharacter(5);
				ret.addCharacter(6);
				ret.addCharacter(curChar>>5);
				ret.addCharacter(curChar&31);
			}
			else
			{
				if (alphabet>0)
				{
					int shift=alphabet+3;
					ret.addCharacter(shift);
				}
				ret.addCharacter(code+6);
			}
		}
		for (int i=0; i<9; i++) // pad
		{
			ret.addCharacter(5);
		}
		return ret;
	}
	
	public int getDictionaryAddress(String token, int dictionary)
	{
		int entryLength = memory[dictionary++]&0xFF;
		int numEntries = makeU16(memory[dictionary+0]&0xFF, memory[dictionary+1]&0xFF);
		dictionary+=2;
		ZDictEntry zde = encodeToken(token);
		for (int i=0; i<numEntries; i++)
		{
			if (zde.coded[0]==(memory[dictionary+0]&0xFF) && zde.coded[1]==(memory[dictionary+1]&0xFF)
					&& zde.coded[2]==(memory[dictionary+2]&0xFF) && zde.coded[3]==(memory[dictionary+3]&0xFF)
					&& (m_version<4
					|| (zde.coded[4]==(memory[dictionary+4]&0xFF) && zde.coded[5]==(memory[dictionary+5]&0xFF))
					))
			{
				return dictionary;
			}
			dictionary+=entryLength;
		}
		return 0;
	}
	
	public int lexicalAnalysis(String input, int parseBuffer, int maxEntries)
	{
		int dictionaryAddress=m_dictionaryTable;
		int numSeperators=memory[dictionaryAddress++];
		String seps="";
		Vector tokens = new Vector();
		for (int i=0; i<numSeperators; i++)
		{
			seps+=(char)memory[dictionaryAddress++];
		}
		
		int currentIdx=0;
		while (currentIdx!=input.length())
		{
			int spaceIdx=input.indexOf(' ', currentIdx);
			int minIdx=input.length();
			boolean sepFound=false;
			if (spaceIdx==currentIdx)
			{
				currentIdx++;
				continue;
			}
			else if (spaceIdx>0)
			{
				minIdx=spaceIdx;
			}
			for (int i=0; i<numSeperators; i++)
			{
				int sepIdx=input.indexOf(seps.charAt(i), currentIdx);
				if (sepIdx==currentIdx)
				{
					ZToken ztok=new ZToken();
					ztok.offset=currentIdx;
					ztok.token=input.substring(currentIdx, currentIdx+1);
					tokens.addElement(ztok);
					currentIdx++;
					sepFound=true;
					break;
				}
				else if (sepIdx>0 && sepIdx<minIdx)
				{
					minIdx=sepIdx;
				}
			}
			if (sepFound)
			{
				continue;
			}
			ZToken ztok=new ZToken();
			ztok.offset=currentIdx;
			ztok.token=input.substring(currentIdx, minIdx);
			tokens.addElement(ztok);
			currentIdx=minIdx;
		}
		
		for (int i=0; i<tokens.size() && i<maxEntries; i++)
		{
			int outAddress=getDictionaryAddress(((ZToken)tokens.elementAt(i)).token, dictionaryAddress);
			memory[parseBuffer++]=(byte)((outAddress>>8)&0xFF);
			memory[parseBuffer++]=(byte)((outAddress>>0)&0xFF);
			memory[parseBuffer++]=(byte)((ZToken)tokens.elementAt(i)).token.length();
			memory[parseBuffer++]=(byte)(((ZToken)tokens.elementAt(i)).offset+1);
		}
		
		return Math.min(maxEntries, tokens.size());
	}
	
	public void process0OPInstruction()
	{
		switch (m_ins.op)
		{
		case 0: //rtrue
			returnRoutine(1);
			break;
		case 1: //rfalse
			returnRoutine(0);
			break;
		case 2: //print
			m_pc=printText(m_pc);
			break;
		case 3: //print_ret
			m_pc=printText(m_pc);
			println();
			returnRoutine(1);
			break;
		case 4: //nop
			break;
		case 5: //save
			if (m_version>=5)
			{
				illegalInstruction();
			}
			else
			{
				m_bNeedInput=true;
				m_bNeedSaving=true;
			}
			break;
		case 6: //restore
			if (m_version>=5)
			{
				illegalInstruction();
			}
			else
			{
				m_bNeedInput=true;
				m_bNeedRestoring=true;
			}
			break;
		case 7: //restart
			restartGame(m_gameName);
			break;
		case 8: //ret_popped
			returnRoutine(((Integer)m_stack.pop()).intValue());
			break;
		case 9: //pop
			if (m_version<5)
				m_stack.pop();
			else
				haltInstruction();
			break;
		case 0xA: //quit
			m_bNeedInput=true;
			m_bNeedToQuit=true;
			break;
		case 0xB: //new_line
			println();
			break;
		case 0xC: //show_status
			if (m_version>3)
			{
				// treated as nop because of bug in V5 Wishbringer
			}
			else
			{
				haltInstruction();
			}
			break;
		case 0xD: //verify
			doBranch(true, m_ins.branch);
			break;
		case 0xE: //extended
			illegalInstruction();
			break;
		case 0xF: //piracy
			doBranch(true, m_ins.branch);
			break;
		}
	}
	
	public void process1OPInstruction()
	{
		switch (m_ins.op)
		{
		case 0: //jz
			doBranch(((ZOperand)m_ins.operands.elementAt(0)).value==0, m_ins.branch);
			break;
		case 1: //get_sibling
		{
			ZObject child=getObject(((ZOperand)m_ins.operands.elementAt(0)).value);
			int siblingId=child.sibling;
			setVariable(m_ins.store, siblingId);
			doBranch(siblingId!=0, m_ins.branch);
			break;
		}
		case 2: //get_child
		{
			ZObject child=getObject(((ZOperand)m_ins.operands.elementAt(0)).value);
			int childId=child.child;
			setVariable(m_ins.store, childId);
			doBranch(childId!=0, m_ins.branch);
			break;
		}
		case 3: //get_parent_object
		{
			ZObject child=getObject(((ZOperand)m_ins.operands.elementAt(0)).value);
			setVariable(m_ins.store, child.parent);
			break;
		}
		case 4: //get_prop_len
		{
			int propAddress=(((ZOperand)m_ins.operands.elementAt(0)).value&0xFFFF)-1;
			int sizeId=memory[propAddress]&0xFF;
			int size;
			if (m_version>3)
			{
				if ((sizeId&0x80)==0x80)
				{
					int sizeId2=memory[propAddress+1]&0xFF;
					size=sizeId2&63;
					if (size==0)
						size=64;
				}
				else
				{
					size=((sizeId&0x40)==0x40)?2:1;
				}
			}
			else
			{
				size=(sizeId>>5)+1;
			}
			setVariable(m_ins.store, size);
			break;
		}
		case 5: //inc
		{
			int value=readVariable(((ZOperand)m_ins.operands.elementAt(0)).value);
			setVariable(((ZOperand)m_ins.operands.elementAt(0)).value, value+1);
			break;
		}
		case 6: //dec
		{
			int value=readVariable(((ZOperand)m_ins.operands.elementAt(0)).value);
			setVariable(((ZOperand)m_ins.operands.elementAt(0)).value, value-1);
			break;
		}	
		case 7: //print_addr
			printText(((ZOperand)m_ins.operands.elementAt(0)).value);
			break;
		case 8: //call_1s
			if (m_version<4)
			{
				illegalInstruction();
			}
			else
			{
				callRoutine(m_packedMultiple*(((ZOperand)m_ins.operands.elementAt(0)).value&0xFFFF), m_ins.store, true);
			}
			break;
		case 9: //remove_obj
		{
			removeObject(((ZOperand)m_ins.operands.elementAt(0)).value);
			break;
		}
		case 0xA: //print_obj
		{
			ZObject obj=getObject(((ZOperand)m_ins.operands.elementAt(0)).value);
			printText(obj.propTable+1);
			break;
		}
		case 0xB: //ret
			returnRoutine(((ZOperand)m_ins.operands.elementAt(0)).value);
			break;
		case 0xC: //jump
			m_pc+=((ZOperand)m_ins.operands.elementAt(0)).value-2;
			break;
		case 0xD: //print_paddr
			printText(m_packedMultiple*(((ZOperand)m_ins.operands.elementAt(0)).value&0xFFFF));
			break;
		case 0xE: //load
			setVariable(m_ins.store, readVariable(((ZOperand)m_ins.operands.elementAt(0)).value));
			break;
		case 0xF: //not
			if (m_version>=5) // call_1n
				callRoutine(m_packedMultiple*(((ZOperand)m_ins.operands.elementAt(0)).value&0xFFFF), -1, true);
			else
				setVariable(m_ins.store, ~((ZOperand)m_ins.operands.elementAt(0)).value);
			break;
		}
	}
	
	public void process2OPInstruction()
	{
		switch (m_ins.op)
		{
		case 0:
			illegalInstruction();
			break;
		case 1: //je
		{
			boolean takeBranch=false;
			int test=((ZOperand)m_ins.operands.elementAt(0)).value;
			for (int i=1; i<m_ins.operands.size(); i++)
			{
				if (test==((ZOperand)m_ins.operands.elementAt(i)).value)
				{
					takeBranch=true;
					break;
				}
			}
			doBranch(takeBranch, m_ins.branch);
			break;
		}
		case 2: //jl
			doBranch(((ZOperand)m_ins.operands.elementAt(0)).value<((ZOperand)m_ins.operands.elementAt(1)).value, m_ins.branch);
			break;
		case 3: //jg
			doBranch(((ZOperand)m_ins.operands.elementAt(0)).value>((ZOperand)m_ins.operands.elementAt(1)).value, m_ins.branch);
			break;
		case 4: //dec_chk
		{
			int value=readVariable(((ZOperand)m_ins.operands.elementAt(0)).value);
			value--;
			setVariable(((ZOperand)m_ins.operands.elementAt(0)).value, value);
			doBranch(value<((ZOperand)m_ins.operands.elementAt(1)).value, m_ins.branch);
			break;
		}	
		case 5: //inc_chk
		{
			int value=readVariable(((ZOperand)m_ins.operands.elementAt(0)).value);
			value++;
			setVariable(((ZOperand)m_ins.operands.elementAt(0)).value, value);
			doBranch(value>((ZOperand)m_ins.operands.elementAt(1)).value, m_ins.branch);
			break;
		}
		case 6: //jin
		{
			ZObject child=getObject(((ZOperand)m_ins.operands.elementAt(0)).value);
			doBranch(child.parent==((ZOperand)m_ins.operands.elementAt(1)).value, m_ins.branch);
			break;
		}
		case 7: //test
		{
			int flags=((ZOperand)m_ins.operands.elementAt(1)).value;
			doBranch((((ZOperand)m_ins.operands.elementAt(0)).value&flags)==flags, m_ins.branch);
			break;
		}
		case 8: //or
			setVariable(m_ins.store, ((ZOperand)m_ins.operands.elementAt(0)).value|((ZOperand)m_ins.operands.elementAt(1)).value);
			break;
		case 9: //and
			setVariable(m_ins.store, ((ZOperand)m_ins.operands.elementAt(0)).value&((ZOperand)m_ins.operands.elementAt(1)).value);
			break;
		case 0xA: //test_attr
		{
			ZObject obj=getObject(((ZOperand)m_ins.operands.elementAt(0)).value);
			int attr=((ZOperand)m_ins.operands.elementAt(1)).value;
			int offset=attr/8;
			int bit=0x80>>(attr%8);
			doBranch((memory[obj.addr+offset]&bit)==bit, m_ins.branch);
			break;
		}
		case 0xB: //set_attr
		{
			ZObject obj=getObject(((ZOperand)m_ins.operands.elementAt(0)).value);
			int attr=((ZOperand)m_ins.operands.elementAt(1)).value;
			int offset=attr/8;
			int bit=0x80>>(attr%8);
			memory[obj.addr+offset]|=bit;
			break;
		}
		case 0xC: //clear_attr
		{
			ZObject obj=getObject(((ZOperand)m_ins.operands.elementAt(0)).value);
			int attr=((ZOperand)m_ins.operands.elementAt(1)).value;
			int offset=attr/8;
			int bit=0x80>>(attr%8);
			memory[obj.addr+offset]&=~bit;
			break;
		}
		case 0xD: //store
			setVariable(((ZOperand)m_ins.operands.elementAt(0)).value, ((ZOperand)m_ins.operands.elementAt(1)).value);
			break;
		case 0xE: //insert_obj
		{
			removeObject(((ZOperand)m_ins.operands.elementAt(0)).value);
			addChild(((ZOperand)m_ins.operands.elementAt(1)).value, ((ZOperand)m_ins.operands.elementAt(0)).value);
			break;
		}
		case 0xF: //loadw
		{
			int address=((((ZOperand)m_ins.operands.elementAt(0)).value&0xFFFF)+2*(((ZOperand)m_ins.operands.elementAt(1)).value&0xFFFF));
			setVariable(m_ins.store, makeS16(memory[address]&0xFF, memory[address+1]&0xFF));
			break;
		}
		case 0x10: //loadb
		{
			int address=((((ZOperand)m_ins.operands.elementAt(0)).value&0xFFFF)+(((ZOperand)m_ins.operands.elementAt(1)).value&0xFFFF));
			setVariable(m_ins.store, memory[address]&0xFF);
			break;
		}
		case 0x11: //get_prop
		{
			ZObject obj=getObject(((ZOperand)m_ins.operands.elementAt(0)).value);
			ZProperty prop=getProperty(obj, ((ZOperand)m_ins.operands.elementAt(1)).value);
			if (prop.size==1)
			{
				setVariable(m_ins.store, memory[prop.addr]&0xFF);
			}
			else if (prop.size==2)
			{
				setVariable(m_ins.store, makeS16(memory[prop.addr]&0xFF, memory[prop.addr+1]&0xFF));
			}
			else
			{
				illegalInstruction();
			}
			break;
		}
		case 0x12: //get_prop_addr
		{
			ZObject obj=getObject(((ZOperand)m_ins.operands.elementAt(0)).value);
			ZProperty prop=getProperty(obj, ((ZOperand)m_ins.operands.elementAt(1)).value);
			if (prop.bDefault)
				setVariable(m_ins.store, 0);
			else
				setVariable(m_ins.store, prop.addr);
			break;
		}
		case 0x13: //get_next_prop
		{
			ZObject obj=getObject(((ZOperand)m_ins.operands.elementAt(0)).value);
			if (((ZOperand)m_ins.operands.elementAt(1)).value==0)
			{
				int address=obj.propTable;
				int textLen=memory[address++]&0xFF;
				address+=textLen*2;
				int nextSizeId=memory[address]&0xFF;
				if (m_version>3)
					setVariable(m_ins.store, nextSizeId&63);
				else
					setVariable(m_ins.store, nextSizeId&31);
			}
			else
			{
				ZProperty prop=getProperty(obj, ((ZOperand)m_ins.operands.elementAt(1)).value);
				if (prop.bDefault)
				{
					illegalInstruction();
				}
				else
				{
					int nextSizeId=memory[prop.addr+prop.size]&0xFF;
					if (m_version>3)
						setVariable(m_ins.store, nextSizeId&63);
					else
						setVariable(m_ins.store, nextSizeId&31);
				}
			}
			break;
		}
		case 0x14: //add
			setVariable(m_ins.store, ((ZOperand)m_ins.operands.elementAt(0)).value+((ZOperand)m_ins.operands.elementAt(1)).value);
			break;
		case 0x15: //sub
			setVariable(m_ins.store, ((ZOperand)m_ins.operands.elementAt(0)).value-((ZOperand)m_ins.operands.elementAt(1)).value);
			break;
		case 0x16: //mul
			setVariable(m_ins.store, ((ZOperand)m_ins.operands.elementAt(0)).value*((ZOperand)m_ins.operands.elementAt(1)).value);
			break;
		case 0x17: //div
			setVariable(m_ins.store, ((ZOperand)m_ins.operands.elementAt(0)).value/((ZOperand)m_ins.operands.elementAt(1)).value);
			break;
		case 0x18: //mod
			setVariable(m_ins.store, ((ZOperand)m_ins.operands.elementAt(0)).value%((ZOperand)m_ins.operands.elementAt(1)).value);
			break;
		case 0x19: //call_2s
			if (m_version<4)
			{
				illegalInstruction();
			}
			else
			{
				callRoutine(m_packedMultiple*(((ZOperand)m_ins.operands.elementAt(0)).value&0xFFFF), m_ins.store, true);
			}
			break;
		case 0x1A: //call_2n
			if (m_version<5)
			{
				illegalInstruction();
			}
			else
			{
				callRoutine(m_packedMultiple*(((ZOperand)m_ins.operands.elementAt(0)).value&0xFFFF), -1, true);
			}
			break;
		case 0x1B: //set_colour
			if (m_version<5)
			{
				illegalInstruction();
			}
			break;
		case 0x1C: //throw
			illegalInstruction();
			break;
		case 0x1D:
		case 0x1E:
		case 0x1F:
			illegalInstruction();
			break;
		}
	}
	
	public void processVARInstruction()
	{
		switch (m_ins.op)
		{
		case 0: // call
			callRoutine(m_packedMultiple*(((ZOperand)m_ins.operands.elementAt(0)).value&0xFFFF), m_ins.store, true);
			break;
		case 1: //storew
		{
			int address=((((ZOperand)m_ins.operands.elementAt(0)).value&0xFFFF)+2*(((ZOperand)m_ins.operands.elementAt(1)).value&0xFFFF));
			int value=((ZOperand)m_ins.operands.elementAt(2)).value;
			memory[address]=(byte)((value>>8)&0xFF);
			memory[address+1]=(byte)(value&0xFF);
			break;
		}
		case 2: //storeb
		{
			int address=((((ZOperand)m_ins.operands.elementAt(0)).value&0xFFFF)+(((ZOperand)m_ins.operands.elementAt(1)).value&0xFFFF));
			int value=((ZOperand)m_ins.operands.elementAt(2)).value;
			memory[address]=(byte)(value&0xFF);
			break;
		}
		case 3: //put_prop
		{
			ZObject obj=getObject(((ZOperand)m_ins.operands.elementAt(0)).value);
			ZProperty prop=getProperty(obj, ((ZOperand)m_ins.operands.elementAt(1)).value);
			if (!prop.bDefault)
			{
				if (prop.size==1)
				{
					memory[prop.addr]=(byte)(((ZOperand)m_ins.operands.elementAt(2)).value&0xFF);
				}
				else if (prop.size==2)
				{
					memory[prop.addr+0]=(byte)((((ZOperand)m_ins.operands.elementAt(2)).value>>8)&0xFF);
					memory[prop.addr+1]=(byte)(((ZOperand)m_ins.operands.elementAt(2)).value&0xFF);
				}
			}
			else
			{
				illegalInstruction();
			}
			break;
		}
		case 4: //sread
		{
			m_bNeedInput=true;
			break;
		}
		case 5: //print_char
			print((char)((ZOperand)m_ins.operands.elementAt(0)).value);
			break;
		case 6: //print_num
			print(String.valueOf(((ZOperand)m_ins.operands.elementAt(0)).value));
			break;
		case 7: //random
		{
			int maxValue=((ZOperand)m_ins.operands.elementAt(0)).value;
			int ret=0;
			if (maxValue>0)
			{
				ret=m_rand.nextInt(maxValue)+1;
			}
			setVariable(m_ins.store, ret);
			break;
		}
		case 8: //push
			setVariable(0, ((ZOperand)m_ins.operands.elementAt(0)).value);
			break;
		case 9: //pull
			setVariable(((ZOperand)m_ins.operands.elementAt(0)).value, readVariable(0));
			break;
		case 0xA: //split_window
			//haltInstruction();
			break;
		case 0xB: //set_window
			//haltInstruction();
			break;
		case 0xC: //call_vs2
			if (m_version<4)
			{
				illegalInstruction();
			}
			else
			{
				callRoutine(m_packedMultiple*(((ZOperand)m_ins.operands.elementAt(0)).value&0xFFFF), m_ins.store, true);
			}
			break;
		case 0xD: //erase_window
			if (m_version<4)
			{
				illegalInstruction();
			}
			break;
		case 0xE: //erase_line
			if (m_version<4)
			{
				illegalInstruction();
			}
			break;
		case 0xF: //set_cursor
			if (m_version<4)
			{
				illegalInstruction();
			}
			break;
		case 0x10: //get_cursor
			illegalInstruction();
			break;
		case 0x11: //set_text_style
			if (m_version<4)
			{
				illegalInstruction();
			}
			break;
		case 0x12: //buffer_mode
			if (m_version<4)
			{
				illegalInstruction();
			}
			break;
		case 0x13: //output_stream
			if (m_version<4)
			{
				haltInstruction();
			}
			break;
		case 0x14: //input_stream
			haltInstruction();
			break;
		case 0x15: //sound_effect
			haltInstruction();
			break;
		case 0x16: //read_char
			if (m_version<4)
			{
				illegalInstruction();
			}
			else
			{
				m_bNeedInput=true;
			}
			break;
		case 0x17: //scan_table
			if (m_version<4)
			{
				illegalInstruction();
			}
			else
			{
				int x = ((ZOperand)m_ins.operands.elementAt(0)).value&0xFFFF;
				int table = ((ZOperand)m_ins.operands.elementAt(1)).value&0xFFFF;
				int len = ((ZOperand)m_ins.operands.elementAt(2)).value&0xFFFF;
				boolean found=false;
				if (m_ins.operands.size()>3)
				{
					illegalInstruction(); // V5 form
				}
				int addr=table;
				for (int i=0; i<len; i++)
				{
					int value=makeU16(memory[addr]&0xFF, memory[addr+1]&0xFF);
					if (value==x)
					{
						found=true;
						break;
					}
					addr+=2;
				}
				setVariable(m_ins.store, found?addr:0);
				doBranch(found, m_ins.branch);
			}
			break;
		case 0x18: //not
			if (m_version<5)
				illegalInstruction();
			else
				setVariable(m_ins.store, ~((ZOperand)m_ins.operands.elementAt(0)).value);
			break;
		case 0x19: //call_vn
			if (m_version<5)
				illegalInstruction();
			else
				callRoutine(m_packedMultiple*(((ZOperand)m_ins.operands.elementAt(0)).value&0xFFFF), -1, true);
			break;
		case 0x1A: //call_vn2
			if (m_version<5)
				illegalInstruction();
			else
				callRoutine(m_packedMultiple*(((ZOperand)m_ins.operands.elementAt(0)).value&0xFFFF), -1, true);
			break;
		case 0x1B: //tokenise
			illegalInstruction();
			break;
		case 0x1C: //encode_text
			illegalInstruction();
			break;
		case 0x1D: //copy_table
			illegalInstruction();
			break;
		case 0x1E: //print_table
			illegalInstruction();
			break;
		case 0x1F: //check_arg_count
			illegalInstruction();
			break;
		}
	}
	
	public void executeInstruction()
	{
		m_ins.operands.removeAllElements();
		//m_logger.debug(Integer.toHexString(m_pc));
		//assertObjectTreeGood();
		int opcode=readBytePC();
		if ((opcode&0xC0)==0xC0)
		{
			readVariableForm(opcode);
		}
		else if ((opcode&0xC0)==0x80)
		{
			readShortForm(opcode);
		}
		else
		{
			readLongForm(opcode);
		}
		switch (m_ins.form)
		{
		case Form0OP:
			if (m_version<=3)
			{
				m_ins.store=readStoreInstruction(zeroOpStoreInstructionsV3,m_ins.op);
				m_ins.branch=readBranchInstruction(zeroOpBranchInstructionsV3,m_ins.op);
			}
			else if (m_version==4)
			{
				m_ins.store=readStoreInstruction(zeroOpStoreInstructionsV4,m_ins.op);
				m_ins.branch=readBranchInstruction(zeroOpBranchInstructionsV45,m_ins.op);
			}
			else if (m_version>=5)
			{
				m_ins.store=readStoreInstruction(zeroOpStoreInstructionsV5,m_ins.op);
				m_ins.branch=readBranchInstruction(zeroOpBranchInstructionsV45,m_ins.op);
			}
			//dumpCurrentInstruction();
			process0OPInstruction();
			break;
		case Form1OP:
			if (m_version<5)
				m_ins.store=readStoreInstruction(oneOpStoreInstructionsV34,m_ins.op);
			else
				m_ins.store=readStoreInstruction(oneOpStoreInstructionsV5,m_ins.op);
			m_ins.branch=readBranchInstruction(oneOpBranchInstructionsV345,m_ins.op);
			//dumpCurrentInstruction();
			process1OPInstruction();
			break;
		case Form2OP:
			m_ins.store=readStoreInstruction(twoOpStoreInstructionsV345,m_ins.op);
			m_ins.branch=readBranchInstruction(twoOpBranchInstructionsV345,m_ins.op);
			//dumpCurrentInstruction();
			process2OPInstruction();
			break;
		case FormVAR:
			if (m_version<5)
				m_ins.store=readStoreInstruction(varOpStoreInstructionsV34,m_ins.op);
			else
				m_ins.store=readStoreInstruction(varOpStoreInstructionsV5,m_ins.op);
			m_ins.branch=readBranchInstruction(varOpBranchInstructionsV345,m_ins.op);
			//dumpCurrentInstruction();
			processVARInstruction();
			break;
		}
	}
	
	void executeUntilInputNeeded()
	{
		if (memory!=null) // check game loaded
		{
			while (!m_bNeedInput)
			{
				executeInstruction();
			}
		}
	}
	
	void giveInput(String input)
	{
		if (m_bNeedInput)
		{
			m_bNeedInput=false;
			m_output="[ >"+input+" ]\n";
			if (m_ins.op==4) // sread
			{
				int bufferAddr=((ZOperand)m_ins.operands.elementAt(0)).value&0xFFFF;
				int parseAddr=((ZOperand)m_ins.operands.elementAt(1)).value&0xFFFF;
				int maxLength=memory[bufferAddr++]&0xFF;
				int maxParse=memory[parseAddr++]&0xFF;
				input=input.toLowerCase();
				for (int i=0; i<input.length() && i<maxLength; i++)
				{
					memory[bufferAddr++]=(byte)input.charAt(i);
				}
				memory[bufferAddr++]=0;
				memory[parseAddr]=(byte)lexicalAnalysis(input, parseAddr+1, maxParse);
			}
			else if (m_ins.op==0x16) // read_char
			{
				input=input.toLowerCase();
				if (input.length()==0)
				{
					setVariable(m_ins.store, 13);
				}
				else
				{
					setVariable(m_ins.store, (int)input.charAt(0));
				}
			}
			else
			{
				illegalInstruction();
			}
		}
	}
}

