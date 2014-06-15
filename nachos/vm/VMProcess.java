package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.util.NoSuchElementException;
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
//		spnTable = new Integer[pageTable.length];
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
//		super.restoreState();
		
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
//		return super.loadSections();
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}
		
		// prep for easy swap-in/out checks later
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				
				//Modification for Proj 3
				TranslationEntry entry = pageTable[vpn];
				
				// set the section number in the vpn to coff sect mapping
				vpnToCoffSect[vpn] = s;
				
//				entry.ppn = thePage;
				entry.valid = false;
				entry.readOnly = section.isReadOnly();
//				section.loadPage(i, entry.ppn);
			}
		}
		
		for (int j = numPages - (stackPages + 1); j < numPages; j++)
		{
			TranslationEntry entry = pageTable[j];
			
			// set the section number in the vpn to coff sect mapping
			// to -3 to indicate it is not a coff page but is stack or args
			vpnToCoffSect[j] = -3;
			
			entry.valid = false;
			entry.readOnly = false;
		}

		return true;
	}
	
	private int getCoffSectOffset(int vpn)
	{
		if (vpnToCoffSect != null) {
			CoffSection coffSect = coff.getSection(vpnToCoffSect[vpn]);
			return vpn - coffSect.getFirstVPN();
		}
		else {
			return -1;
		}
		
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		// TODO:
		// handle removing this process's owned swap pages before unloading
		// the pages using the super class's unloadSections
		
		super.unloadSections();
	}
	
	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
//		Lib.assertTrue(offset >= 0 && length >= 0
//				&& offset + length <= data.length);

		if(!validMemoryArgs(vaddr, data, offset, length))
			return 0;
		
		byte[] memory = Machine.processor().getMemory();
		int vpn = Processor.pageFromAddress(vaddr);
		
		int vpnOffset = Processor.offsetFromAddress(vaddr);
		TranslationEntry entry = pageTable[vpn];
		
		validateEntry(vpn, entry);
		// entry should now be valid
		
		entry.used = true;
		int realAddr = entry.ppn*pageSize + vpnOffset;
		
		// for now, just assume that virtual addresses equal physical addresses
		if (realAddr < 0 || realAddr >= memory.length)
		{
//			entry.used = false;
			return 0;
		}
		
		// keeps track of what's written to data
		int written = 0;
		int bufOffset = offset;
		int pageOffset = vpnOffset;
		int currAddr = realAddr;
		int leftToWrite = length;
		int currVpn = vpn;
		int currPpn = entry.ppn;
		
		while (written < length)
		{
			if (pageOffset + leftToWrite > pageSize)
			{
				int amountToWrite = pageSize - pageOffset;
				System.arraycopy(memory, currAddr, data, bufOffset, amountToWrite);
				written += amountToWrite;
				bufOffset += amountToWrite;
				leftToWrite = length - written;
				if (++currVpn >= numPages)
					break;
				else
				{
//					pageTable[currVpn - 1].used = false;
					TranslationEntry currEntry = pageTable[currVpn];
					if (!currEntry.valid)
						validateEntry(currVpn, currEntry);
					
					currEntry.used = true;
					pageOffset = 0;
					currPpn = currEntry.ppn;
					currAddr = currPpn * pageSize;
				}
			}
			else
			{
				System.arraycopy(memory, currAddr, data, bufOffset, leftToWrite);
				written += leftToWrite; // written should now equal length
				bufOffset += leftToWrite;
			}
			
		}
//		pageTable[currVpn].used = false;
		return written;
	}
	
	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
//		Lib.assertTrue(offset >= 0 && length >= 0
//				&& offset + length <= data.length);

		// checks to make sure the args are valid
		if(!validMemoryArgs(vaddr, data, offset, length))
			return 0;
		
		byte[] memory = Machine.processor().getMemory();
		int vpn = Processor.pageFromAddress(vaddr);
		
		int vpnOffset = Processor.offsetFromAddress(vaddr);
		TranslationEntry entry = pageTable[vpn];
		
		// ensures the current entry is a valid one or makes it valid
		validateEntry(vpn, entry);
		// entry should now be valid
		
		entry.used = true;
		int realAddr = entry.ppn*pageSize + vpnOffset;
		
		// for now, just assume that virtual addresses equal physical addresses
		if (realAddr < 0 || realAddr >= memory.length)
		{
//			entry.used = false;
			return 0;
		}
		
		// keeps track of what's written to data
		int written = 0;
		int bufOffset = offset;
		int pageOffset = vpnOffset;
		int currAddr = realAddr;
		int leftToWrite = length;
		int currVpn = vpn;
		int currPpn = entry.ppn;
		
		while (written < length)
		{
			if (pageOffset + leftToWrite > pageSize)
			{
				int amountToWrite = pageSize - pageOffset;
				System.arraycopy(data, bufOffset, memory, currAddr, amountToWrite);
				written += amountToWrite;
				bufOffset += amountToWrite;
				leftToWrite = length - written;
				if (++currVpn >= numPages)
					break;
				else
				{
//					pageTable[currVpn - 1].used = false;
					TranslationEntry currEntry = pageTable[currVpn];
					
					// Make sure the next page is also not read only
					if (currEntry.readOnly) {
						Lib.debug(dbgProcess, "Somehow next entry is readOnly");
						break;
					}
						
					if (!currEntry.valid)
						validateEntry(currVpn, currEntry);
					
					currEntry.used = true;
					pageOffset = 0;
					currPpn = currEntry.ppn;
					currAddr = currPpn * pageSize;
				}
			}
			else
			{
				System.arraycopy(data, bufOffset, memory, currAddr, leftToWrite);
				written += leftToWrite; // written should now equal length
				bufOffset += leftToWrite;
			}
			
		}
//		pageTable[currVpn].used = false;
		return written;
	}
	
	private boolean validMemoryArgs(int vaddr, byte[] data, int offset, int length) {
		if (data == null)
			return false;
		
		if (!(offset >= 0 && length >= 0
				&& offset + length <= data.length))
			return false;
		
		byte[] memory = Machine.processor().getMemory();

		if (vaddr < 0 || vaddr >= memory.length)
			return false;
		
		
		int vpn = Processor.pageFromAddress(vaddr);
		
		// vpn outside this program's address space
		if (vpn >= numPages) {
			return false;
		}
		
		// if it gets this far, the args are ok
		return true;
	}

	private void validateEntry(int vpn, TranslationEntry entry) {
		if (!entry.valid) {
			// handle making the page valid be it reading from the coff section
			// or swapping in a page from memory
			if (entry.dirty)
			{
				entry.ppn = VMKernel.swapIn(vpn, this);
				entry.valid = true;
			}
			else { // entry not dirty
				int coffSectNum = vpnToCoffSect[vpn];
				entry.ppn = VMKernel.allocPage(vpn, this, false, entry.readOnly);
				
				// if in a coff section, we need to load it
				if (coffSectNum != -3) {
					// Get a page from the coff file
					CoffSection sect = coff.getSection(coffSectNum);
					sect.loadPage(getCoffSectOffset(vpn), entry.ppn);

				}
				// just a new blank page for stack or args
				entry.valid = true;
			}
		}
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
//            handlePageFault(f);
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
		
		// kernel needs a static condition variable for all pages pinned
		int ppn = -1;
		int originalHand = clockhand;
		
		do
		{
			ppn = VMKernel.allocPage(fault, this, false, faultEntry.readOnly);
			
			if (ppn < 0)
			{
				VMKernel.MetaData currMet = VMKernel.iPageTable[clockhand];
				
				if(!currMet.pinned)
				{
					// possible to be evicted if not pinned
					TranslationEntry currEntry = currMet.ownProcess.pageTable[currMet.vpn];
					if(currEntry.used)
						currEntry.used = false;
					else {
						if (currEntry.dirty) {
							// evict and swap
							
						}
						else {
							// evict without swapping
							currEntry.valid = false;
							// 
							
							//
						}
					}
				}
				
				clockhand = (clockhand + 1) % VMKernel.iPageTable.length;
				if (clockhand == originalHand)
				{
					// condition variable wait
				}
			}
			
		} while (ppn < 0);
		
		//fault ppn?
		
		//VMKernel.invertedPageTable[ppn] = this;
		
		for(int i=0; i<coff.getNumSections(); i++) {
			
			CoffSection sect = coff.getSection(i);
			
			
		}
	}
	
	private static int clockhand = 0;
	
	private int[] vpnToCoffSect;
	
//	private Integer[] spnTable;
	
	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}
