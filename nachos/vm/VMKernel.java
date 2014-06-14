package nachos.vm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();

		swapSpace = new HashMap<MetaData, TranslationEntry>();
		pagesCanBeSwapped = new ArrayList<TranslationEntry>();
		freePages = new LinkedList<Integer>();
		pinnedPages = new ArrayList<Integer>();
		
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
				
		pinnedPages = new ArrayList<Integer>();
		pinLock = new Lock();
		iptLock = new Lock();
		
		for (int i = 0; i < iPageTable.length; i++)
		{
			iPageTable[i] = null;
		}
		
		swapFile = ThreadedKernel.fileSystem.open(swapFile.getName(), true);
		
		super.initialize(args);
	}

	public static class MetaData {
		// virtual page number
		int vpn;
		
		// owning process
		VMProcess ownProcess;
		
		// pinned condition
		boolean pinned;
		
		public MetaData(int vpn, VMProcess ownProcess, boolean pinned)
		{
			this.vpn = vpn;
			this.ownProcess = ownProcess;
			this.pinned = pinned;
		}
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		//Close swapfile
		swapFile.close();
        ThreadedKernel.fileSystem.remove(swapFile.getName());
		
		super.terminate();
	}
	

	
	public void pinPage(Integer i){
        pinLock.acquire();
        pinnedPages.add(i);
        pinLock.release();
    }
	
	public void unPinPage(Integer i){
        pinLock.acquire();
        pinnedPages.remove(i);
        pinLock.release();
    }
	
	public boolean contains(TranslationEntry i){
		pinLock.acquire();
		boolean result = pinnedPages.contains(i);
		pinLock.release();
		return result;
	}
	
	
	public int swapOut(){
        TranslationEntry swapPage = null;

        if(!pagesCanBeSwapped.isEmpty()){
            int i =0;
            while(i< pagesCanBeSwapped.size())
            {
                //swapLock.acquire();
                swapPage = pagesCanBeSwapped.get(i);
                //swapLock.release();
                
                if(!contains(swapPage)){
                    //swapLock.acquire();
                    swapPage = pagesCanBeSwapped.remove(i);
                    //swapLock.release();
                    break;
                }
                i = (i+1) % (pagesCanBeSwapped.size());
            }
        }
        int removed = -1;
        
        iptLock.acquire();
        for(int i = 0; i < iPageTable.length; i++){
        	
        	//index is the ppn
            if(i == swapPage.ppn){
                removed = i;
                break;
            }
        }

        if(removed!=-1)
        	iPageTable[removed] = null;
        iptLock.release();

        int size = Processor.pageSize;
        
        byte[] pageContents = new byte[size];
        byte[] memory = Machine.processor().getMemory();

        System.arraycopy(memory, swapPage.ppn*size, pageContents, 0, size);
        int loc = freePages.removeFirst()*size;
        swapFile.write(loc, pageContents, 0, size);
        MetaData removedPage = iPageTable[removed];
        swapSpace.put(removedPage,swapPage);
        //
        return swapPage.ppn;
    }
	
	public int swapIn(int vpn, VMProcess process){
        TranslationEntry freeEntry = getFreePage(vpn);
        MetaData data = new MetaData(vpn, process, false);
        
        int loc = diskLoc.get(data);
        int size = Processor.pageSize;
        
        byte[] pageContent = new byte[size];


        swapFile.read(loc, pageContent,0,size);
        byte[] mem = Machine.processor().getMemory();
        System.arraycopy(pageContent,0,mem,freeEntry.ppn*size,size);
        swapSpace.remove(data);
        freePages.add(freeEntry.ppn);
        
        diskLoc.remove(data);

        return freeEntry.ppn;

    }
	
	//Define Variables
	//*******************************************************************************************
	
	protected ArrayList<Integer> pinnedPages;
	protected ArrayList<TranslationEntry> pagesCanBeSwapped;
	protected static LinkedList<Integer> freePages;
	protected HashMap<MetaData, TranslationEntry> swapSpace;
	protected HashMap<MetaData, Integer> diskLoc; 
	public static OpenFile swapFile;
	private Lock pinLock;
	
	private Lock iptLock;
	private MetaData[] iPageTable = new MetaData[Machine.processor().getNumPhysPages()]; 

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';
}
