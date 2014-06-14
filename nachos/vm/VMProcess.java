package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.Random;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
		
		for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
	        TranslationEntry entry = Machine.processor().readTLBEntry(i);
	        if (entry.valid) {
	        	entry.valid = false;
	            Machine.processor().writeTLBEntry(i, entry);
	            pageTable[entry.vpn] = new TranslationEntry(entry);
	        }
	    }
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		super.restoreState();
		
		//Is this needed??
		for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
	        TranslationEntry entry = Machine.processor().readTLBEntry(i);
	        if (entry.valid) {
	        	entry.valid = false;
	            Machine.processor().writeTLBEntry(i, entry);
	            pageTable[entry.vpn] = new TranslationEntry(entry);
	        }
	    }
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		return super.loadSections();
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		
		case Processor.exceptionTLBMiss:
			int e = Machine.processor().readRegister(Processor.regBadVAddr);
            handleTLBMiss(e);
            break;
		case Processor.exceptionPageFault:
//			int f = Machine.processor().readRegister(Processor.regBadVAddr);
//            handleTLBMiss(f);
            break;
		default:
			super.handleException(cause);
			break;
		}
	}

	
	protected void handleTLBMiss(int miss){
        
        int tlbToBeSwapped = -1;
        
        //loops through all the entries of the tlb checking to see if its valid.
        //If not valid, designate it as the one to be swapped out
        for(int i = 0; i < Machine.processor().getTLBSize(); i++){
        	
        	//If we haven't found a tlb to be swapped
            if(tlbToBeSwapped == -1){
                
                TranslationEntry entry = Machine.processor().readTLBEntry(i);
                
                //Set the tlb to be swapped as this one cuz it's not valid
                if (!entry.valid)
                    tlbToBeSwapped = i;
            }
        }
        
        //If all tlb entries are valid, pick a random one to swap out
        if(tlbToBeSwapped == -1){
        	
        	//Need to seed?
        	Random random = new Random();
        	tlbToBeSwapped = random.nextInt(Machine.processor().getTLBSize());
        	
        	TranslationEntry swapEntry = Machine.processor().readTLBEntry(tlbToBeSwapped);
        	
        	//Check entry against page table entry for validity
        	if (swapEntry.valid) {
                this.pageTable[swapEntry.vpn].dirty = swapEntry.dirty;
                this.pageTable[swapEntry.vpn].used = swapEntry.used;
            }
        }
        
        //Get the translation entry for the missed entry
        TranslationEntry entryToBeAdded = new TranslationEntry(pageTable[miss]);
        
        while (!entryToBeAdded.valid) {
            handlePageFault(miss);
            entryToBeAdded = new TranslationEntry(pageTable[miss]);
        }
        
        //Finally add the entry in the TLB :) 
        Machine.processor().writeTLBEntry(tlbToBeSwapped, entryToBeAdded);
	}
	
	protected void handlePageFault(int fault){
		
		TranslationEntry faultEntry = pageTable[fault];
		
		faultEntry.vpn = fault;
		faultEntry.valid = true;
		//fault ppn?
		
		//VMKernel.invertedPageTable[ppn] = this;
		
		for(int i=0; i<coff.getNumSections(); i++) {
			
			CoffSection sect = coff.getSection(i);
			
			
		}
	}
	
	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}
